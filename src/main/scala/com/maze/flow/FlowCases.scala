package com.maze.flow

import akka.NotUsed
import akka.stream. Materializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Framing, Keep, MergeHub, Sink, Source}
import akka.util.ByteString
import com.maze.data.{Broadcast, Event, Follow, PrivateMsg, StatusUpdate, Unfollow, UserId}

import scala.collection.SortedSet
import scala.concurrent.{ExecutionContext, Future, Promise}

object FlowCases {
  val maxLineSize = 1024

  val inputFlow: Flow[ByteString, String, NotUsed] =
    Framing
      .delimiter(ByteString("\n"), maxLineSize)
      .map(_.decodeString("UTF8"))

  val eventParseFlow: Flow[ByteString, Event, NotUsed] =
    inputFlow.map(Event.parse)

  def orderedFlow(groupSize: Int): Flow[Event, Event, NotUsed] =
    Flow[Event].grouped(groupSize).mapConcat {
      events =>
        implicit val idOrdering: Ordering[Event] = Ordering.by(_.id)
        val buffer = SortedSet[Event](events: _*)
        buffer.toList
    }

  val followersFlow: Flow[Event, (Event, Map[Int, Set[Int]]), NotUsed] =
    Flow[Event].statefulMapConcat { () =>
      var followers = Map[Int, Set[Int]]().withDefaultValue(Set())

      element => {
        element match {
          case Follow(_, fromUserId, toUserId) => followers += fromUserId -> (followers(fromUserId) + toUserId)
          case Unfollow(_, fromUserId, toUserId) => followers += fromUserId -> (followers(fromUserId) - toUserId)
          case _ =>
        }

        (element, followers) :: Nil
      }
    }

  val userIdSink: Sink[ByteString, Future[UserId]] =
    inputFlow.map(UserId(0).parse).toMat(Sink.head)(Keep.right)

  def isNotified(userId: Int)(eventAndFollowers: (Event, Map[Int, Set[Int]])): Boolean =
    eventAndFollowers._1 match {
      case Follow(_, _, toUserId) => toUserId == userId
      case PrivateMsg(_, _, toUserId) => toUserId == userId
      case StatusUpdate(_, fromUserId) => eventAndFollowers._2(userId).contains(fromUserId)
      case Broadcast(_) => true
      case _ => false
    }
}

class FlowCases(orderGroupSize: Int)(implicit executionContext: ExecutionContext, materializer: Materializer) {
  import FlowCases._

  val (inputSink, broadcastOut) = {

    val inputDataFlow: Flow[ByteString, (Event, Map[Int, Set[Int]]), NotUsed] =
      eventParseFlow.via(orderedFlow(orderGroupSize)).via(followersFlow)

    MergeHub.source[ByteString](256)
      .via(inputDataFlow)
      .toMat(BroadcastHub.sink(256))(Keep.both)
      .run()
  }

  def outgoingFlow(userId: Int): Source[ByteString, NotUsed] =
    broadcastOut
      .filter(isNotified(userId))
      .map(_._1.toByte)

  def subscribeFlow() : Flow[ByteString, ByteString, NotUsed] = {
    val clientIdPromise = Promise[UserId]()
    val incoming: Sink[ByteString, NotUsed] =
      userIdSink.mapMaterializedValue { value =>
        clientIdPromise.completeWith(value)
        NotUsed
      }

    val outgoing = Source.fromFutureSource(clientIdPromise.future.map { user =>
      outgoingFlow(user.userId)
    })

    Flow.fromSinkAndSource(incoming, outgoing)
  }

  val eventSourceFlow: Flow[ByteString, Nothing, NotUsed] =
    Flow.fromSinkAndSourceCoupled(inputSink, Source.maybe)
}
