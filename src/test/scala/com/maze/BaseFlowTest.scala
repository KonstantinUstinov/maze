package com.maze

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ActorAttributes, ActorMaterializer, Materializer}
import akka.testkit.TestKit
import akka.util.ByteString
import com.maze.data.{Broadcast, Event, Follow, PrivateMsg, StatusUpdate, Unfollow, UserId}
import com.maze.flow.FlowCases
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

class BaseFlowTest extends TestKit(ActorSystem("BaseFlowTest")) with FunSuiteLike with BeforeAndAfterAll with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(6000, Millis)), interval = scaled(Span(4000, Millis)))

  implicit val materializer: Materializer = ActorMaterializer()
  implicit def executor: ExecutionContextExecutor = system.dispatcher

  override protected def afterAll(): Unit = {
    shutdown(system)
  }

  test("incomingFlow: 1 message should pass") {
    val input = List(ByteString("input_first\n"), ByteString("input_second\n"))
    val result = Source(input).via(FlowCases.inputFlow).runWith(Sink.seq).futureValue
    assert(result === Seq("input_first", "input_second"))
  }

  test("eventParseFlow: parse event") {
    val input = List(
      ByteString("666|F|60|50\n"),
      ByteString("1|U|12|9\n"),
      ByteString("54232|B\n"),
      ByteString("634|S|32\n"),
      ByteString("43|P|32|56\n")
    )

    val result = Source(input).via(FlowCases.eventParseFlow).runWith(Sink.seq).futureValue
    assert(result === Seq(
      Follow(666, 60, 50),
      Unfollow(1, 12, 9),
      Broadcast(54232),
      StatusUpdate(634, 32),
      PrivateMsg(43, 32, 56))
    )
  }

  test ("orderedFlow: order events") {
    val input = List(
      Follow(2, 10, 20),
      Follow(3, 20, 30),
      Follow(1, 30, 40)
    )

    val result = Source(input).via(FlowCases.orderedFlow(5)).runWith(Sink.seq).futureValue

    assert(result === input.sortBy(_.id))
  }

  test("followersFlow: remove a follower") {
    val result = Source(List(Follow(1, 1, 2), Unfollow(2, 1, 2))).via(FlowCases.followersFlow).runWith(Sink.seq).futureValue
    assert(result === Seq((Follow(1, 1, 2), Map(1 -> Set(2))), (Unfollow(2, 1, 2), Map(1 -> Set.empty))))
  }

  test("userIdSink: parse UserId"){
    assert(Source.single(ByteString("567\n")).runWith(FlowCases.userIdSink).futureValue === UserId(567))
  }

  test("isNotified: notify the followers"){
    assert(FlowCases.isNotified(0)((Broadcast(1), Map.empty)))
    assert(FlowCases.isNotified(10)((Follow(1, 1, 10), Map.empty)))
    assert(FlowCases.isNotified(12)((PrivateMsg(1, 1, 12), Map.empty)))
    assert(FlowCases.isNotified(14)((StatusUpdate(1, 16), Map(14 -> Set(16)))))
    assert(!FlowCases.isNotified(14)((StatusUpdate(1, 16), Map(14 -> Set(15, 17)))))
  }

  test("eventSourceFlow: reframe, reorder and compute followers") {
    val server = new FlowCases(2)
    val eventsProbe = connectTestEvents(server)(List(
      StatusUpdate(2, 2),
      Follow(1, 1, 2)
    ))
    val outProbe = server.broadcastOut.runWith(TestSink.probe)
    outProbe.ensureSubscription().request(Int.MaxValue)
    outProbe.expectNext((Follow(1, 1, 2), Map(1 -> Set(2))))
    outProbe.expectNext((StatusUpdate(2, 2), Map(1 -> Set(2))))
    outProbe.expectNoMessage(50.millis)
    eventsProbe.ensureSubscription().request(1).expectComplete()
  }

  test("Integration Test") {
    val server = new FlowCases(2)
    val client1 = connectClient(1, server)
    val client2 = connectClient(2, server)
    val client3 = connectClient(3, server)

    val eventsProbe = connectTestEvents(server)(List(
      StatusUpdate(2, 2),
      Follow(1, 1, 2)
    ))

    client1.expectNext(StatusUpdate(2, 2))
    client1.expectNoMessage(50.millis)
    client2.expectNext(Follow(1, 1, 2))
    client2.expectNoMessage(50.millis)
    client3.expectNoMessage(50.millis)
    eventsProbe.ensureSubscription().request(1).expectComplete()
  }

  test("Integration Test @@2") {
    val server = new FlowCases(2)
    val follower1 = connectClient(1, server)
    val follower2 = connectClient(2, server)
    val eventsProbe = connectTestEvents(server)(List(
      StatusUpdate(2, 2),
      Follow(1, 1, 2),
      StatusUpdate(3, 1)
    ))

    follower2.expectNext(Follow(1, 1, 2))
    follower2.expectNoMessage(50.millis)
    follower1.expectNext(StatusUpdate(2, 2))
    follower1.expectNoMessage(50.millis)
    eventsProbe.ensureSubscription().request(1).expectComplete()
  }

  def connectTestEvents(flow: FlowCases)(events: List[Event]): TestSubscriber.Probe[Nothing] = {
    Source(events)
      .via(Flow[Event])
      .map(_.toByte).async
      .via(flow.eventSourceFlow)
      .runWith(TestSink.probe)
  }

  def connectClient(id: Int, flow: FlowCases): TestSubscriber.Probe[Event] = {
    Source.single(ByteString(s"$id\n"))
      .async
      .via(flow.subscribeFlow())
      .via(FlowCases.eventParseFlow)
      .runWith(TestSink.probe)
      .ensureSubscription()
      .request(Int.MaxValue)
  }

}
