# maze
 Coding Challenge.

How to Run

sbt run

OR

sbt assembly

cd target/scala-2.12

java -jar maze-assembly-0.1-SNAPSHOT.jar



DESCRIPTION

The main problem is sorting. I decided to use chunks 500K size and sort in chunks.
I think it is not the best solution but sort in memory all messages - not so good too.

Why i decided to use akka-stream?

The library provides functional abstractions called "flows" for performing reusable units of transformation on reactive streams. Flows encapsulate most of the statefulness involved in stream processing and yield concise, modular, and composable code. As a result, I find that akka-streams makes complex concurrent operations easier to reason about and maintain over time. The library is also quite performant, well-documented, and comes with handy built-in unit-testing tools.