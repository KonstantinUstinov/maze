package com.maze

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp
import akka.stream.{ActorMaterializer, Materializer}
import com.maze.flow.FlowCases

import scala.concurrent.{ExecutionContextExecutor, Future}

object Boot extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = ActorMaterializer()
  implicit def executor: ExecutionContextExecutor = system.dispatcher

  val host = "127.0.0.1"
  val eventSourcePort = 9090
  val userClientPort = 9099

  val orderGroupSize = 500000

  println(s"+ Server online at $host")

  val flow = new FlowCases(orderGroupSize)

  Future.sequence(Seq(
    createUserClient(),
    createEventSource()))

  def createUserClient(): Future[Done] = {
    val userClientSocket = Tcp().bind(host, userClientPort)

    userClientSocket runForeach { cnn =>
      cnn.handleWith(flow.subscribeFlow())
    }
  }

  def createEventSource(): Future[Done] = {
    val eventSourceSocket = Tcp().bind(host, eventSourcePort)

    eventSourceSocket  runForeach { cnn =>
      cnn.handleWith(flow.eventSourceFlow)
    }
  }

}
