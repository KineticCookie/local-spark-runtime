package org.kineticcookie.serve

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings

import scala.concurrent.duration._
import scala.reflect.runtime.universe._

import io.hydrosphere.mist.api.ml._
import LocalPipelineModel._
import MapAnyJson._
import spray.json._
import DefaultJsonProtocol._
import SparkUtils._

/**
  * Created by Bulat on 19.05.2017.
  */
object Boot extends App {
  def modelPath(modelName: String) = s"../models/$modelName"
  def convertCollection[T: TypeTag](list: List[T]) = {
    list match {
      case value: List[Double @unchecked] =>
        value.toArray
      case value: List[Int @unchecked] =>
        value.toArray
      case e => throw new IllegalArgumentException(e.toString)
    }
  }

  implicit val system = ActorSystem("ml_server")
  implicit val materializer = ActorMaterializer()
  implicit val ex = system.dispatcher
  implicit val timeout = Timeout(10.seconds)

  val corsSettings = CorsSettings.defaultSettings

  val routes = cors(corsSettings) {
    get {
     path("health") {
       complete {
         "Hi"
       }
     }
    }~
    post {
      path(Segment) { modelName =>
        import MapAnyJson._
          entity(as[List[Map[String, Any]]]) { mapList =>
            complete {
              println(s"Incoming request. Model: $modelName. Params: $mapList")
              try {
                val pipelineModel = PipelineLoader.load(modelPath(modelName))
                println("Pipeline loaded")
                val inputCols = pipelineModel.getInputColumns
                val columns = inputCols.map { colName =>
                  val colData = mapList.map { col =>
                    val data = col(colName)
                    data match {
                      case l: List[Any] => convertCollection(l)
                      case x => x
                    }
                  }
                  LocalDataColumn(colName, colData)
                }
                val inputLDF = LocalData(columns.toList)
                println("Local DataFrame created")
                val result = pipelineModel.transform(inputLDF)
                println(s"Result: ${result.toMapList}")
                result.select(pipelineModel.getOutputColumns :_*).toMapList.asInstanceOf[List[Any]]
              } catch {
                case e: Exception =>
                  println(e.toString)
                  e.getStackTrace.foreach(println)
                  e.toString
              }
            }
          }
      }
    }
  }

  Http().bindAndHandle(routes, "0.0.0.0", 8080)
}
