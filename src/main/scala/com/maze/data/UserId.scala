package com.maze.data

case class UserId(userId: Int) {
  def parse(msg: String): UserId = UserId(msg.toInt)
}
