import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.AssemblyPlugin.defaultShellScript

import scalariform.formatter.preferences._

lazy val akka =
  Seq(libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % "2.8.5",
    "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
    "com.typesafe.akka" %% "akka-slf4j" % "2.8.5",
    "com.typesafe.akka" %% "akka-stream" % "2.8.5",
    "com.typesafe.akka" %% "akka-stream-typed" % "2.8.5",
    "com.typesafe.akka" %% "akka-http-core" % "10.5.2",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.8.5" % "test",
    "com.iheart" %% "ficus" % "1.5.2",
    "ch.qos.logback" % "logback-classic" % "1.4.11"))

lazy val assemble =
  Seq(assembly / assemblyJarName := name.value,
    assembly / target := new File("bin/"),
    assembly / assemblyCacheOutput := false,
    assembly / assemblyPrependShellScript := Some(defaultShellScript :+ ""),
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.discard
      case PathList("META-INF", _*) => MergeStrategy.discard
      case PathList(ps @ _*) if ps.last.endsWith(".proto") => MergeStrategy.first
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    assembly / test := {})

lazy val scopt = Seq(libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0")

lazy val specs2 = Seq(libraryDependencies += "org.specs2" %% "specs2-core" % "4.20.2" % "test",
  Test / fork := true)

lazy val csv = Seq(libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.10")

lazy val mysql =
  Seq(libraryDependencies ++= Seq("org.apache.commons" % "commons-dbcp2" % "2.10.0",
    "commons-dbutils" % "commons-dbutils" % "1.8.1",
    "com.mysql" % "mysql-connector-j" % "8.1.0"))

lazy val scalaz = Seq(libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.3.7")

lazy val common = Defaults.coreDefaultSettings ++ assemble ++
  Seq(scalaVersion := "2.13.11",
    scalariformAutoformat := true,
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignArguments, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentConstructorArguments, true)
      .setPreference(SpacesWithinPatternBinders, false)
      .setPreference(SpacesAroundMultiImports, false))

lazy val stk = project
  .in(file("."))
  .settings(name := "stk")
  .aggregate(replicate, wipe, stats, loader, sms)

lazy val stats = project
  .in(file("stats"))
  .settings(name := "stats", common, akka, scopt)
  .dependsOn(canape, steenwerck)

lazy val replicate = project
  .in(file("replicate"))
  .settings(name := "replicate", common, akka, scopt, specs2, csv, scalaz, Revolver.settings)
  .dependsOn(canape, steenwerck, rxtelegram, octopush)

lazy val loader = project
  .in(file("loader"))
  .settings(name := "loader", common, akka, mysql, scopt, Revolver.settings)
  .dependsOn(canape, steenwerck)

lazy val wipe = project
  .in(file("wipe"))
  .settings(name := "wipe", common, akka, scopt)
  .dependsOn(canape, steenwerck)

lazy val sms = project
  .in(file("sms"))
  .settings(name := "sms", common, akka, scopt)
  .dependsOn(octopush)

lazy val canape = RootProject(file("external/canape"))

lazy val octopush = RootProject(file("external/octopush-akka"))

lazy val steenwerck = project
  .in(file("libs/steenwerck"))
  .settings(name := "Steenwerck", common, akka)
  .dependsOn(canape)

lazy val rxtelegram = RootProject(file("external/rxtelegram"))
