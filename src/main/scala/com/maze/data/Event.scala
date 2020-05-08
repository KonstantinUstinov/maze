package com.maze.data

import akka.util.ByteString

sealed trait Event {
  def id: Int
  def toByte: ByteString
}

object Event {

  def parse(input: String): Event = {
    val fieldsArr = input.split("\\|")
    val id = fieldsArr.head.toInt

    fieldsArr.tail.head match {
      case "F" => Follow(id, fieldsArr(2).toInt, fieldsArr(3).toInt)
      case "U" => Unfollow(id, fieldsArr(2).toInt, fieldsArr(3).toInt)
      case "B" => Broadcast(id)
      case "P" => PrivateMsg(id, fieldsArr(2).toInt, fieldsArr(3).toInt)
      case "S" => StatusUpdate(id, fieldsArr(2).toInt)
    }
  }
}

final case class Follow(id: Int, fromUserId: Int, toUserId: Int) extends Event {
  def toByte: ByteString = ByteString(s"$id|F|$fromUserId|$toUserId\n")
}

final case class Unfollow(id: Int, fromUserId: Int, toUserId: Int) extends Event {
  def toByte: ByteString = ByteString(s"$id|U|$fromUserId|$toUserId\n")
}

final case class Broadcast(id: Int) extends Event {
  def toByte: ByteString = ByteString(s"$id|B\n")
}

final case class PrivateMsg(id: Int, fromUserId: Int, toUserId: Int) extends Event {
  def toByte: ByteString = ByteString(s"$id|P|$fromUserId|$toUserId\n")
}

final case class StatusUpdate(id: Int, fromUserId: Int) extends Event {
  def toByte: ByteString = ByteString(s"$id|S|$fromUserId\n")
}
