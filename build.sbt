name := "traffic_light"

version := "0.1"

scalaVersion := "2.12.6"

val logback = "ch.qos.logback" % "logback-classic" % "latest.release"
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "latest.release"
val akkaTypedPersistence = "com.typesafe.akka" %% "akka-persistence-typed" % "2.5.23"
val lableDb = "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"
val leveldB = "org.iq80.leveldb"            % "leveldb"          % "0.7"

libraryDependencies ++= Seq(
  logback,
  scalaLogging,
  akkaTypedPersistence,
  leveldB,
  lableDb
)
