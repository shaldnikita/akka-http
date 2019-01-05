package uploading

import java.io.File

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.javadsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing, Sink}
import akka.util.ByteString

import scala.concurrent.Future


class UploadFile {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end
  implicit val executionContext = system.dispatcher
  val metadataActor:ActorRef = ???

  val uploadVideo =
    path("video") {
      entity(as[Multipart.FormData]) { formData => {
        val allPartsF: Future[Map[String, Any]] = formData.parts.mapAsync[(String, Any)](1) {
          case b: BodyPart if b.name == "file" =>
            // stream into a file as the chunks of it arrives and return a future
            // file to where it got stored
            val file = File.createTempFile("upload", "tmp")
            b.entity.dataBytes.runWith(FileIO.toPath(file.toPath)).map(_ => b.name -> file)

          case b: BodyPart =>
            // collect form field values
            b.toStrict(2.seconds).map(strict => b.name -> strict.entity.data.utf8String)

        }.runFold(Map.empty[String, Any])((map, tuple) => map + tuple)

        val done = allPartsF.map { allParts =>
          // You would have some better validation/unmarshalling here
        }

        // when processing have finished create a response for the user
        onSuccess(allPartsF) { allParts =>
          complete {
            "ok!"
          }
        }
      }
      }
    }
  val splitLines = Framing.delimiter(ByteString("\n"), 256)

  val csvUploads =
    path("metadata" / LongNumber) { id =>
      entity(as[Multipart.FormData]) { formData =>
        val done: Future[Done] = formData.parts.mapAsync(1) {
          case b: BodyPart if b.filename.exists(_.endsWith(".csv")) =>
            b.entity.dataBytes
              .via(splitLines)
              .map(_.utf8String.split(",").toVector)
              .runForeach(csv =>
                metadataActor ! MetadataActor.Entry(id, csv))
          case _ => Future.successful(Done)
        }.runWith(Sink.ignore)

        // when processing have finished create a response for the user
        onSuccess(done) { _ =>
          complete {
            "ok!"
          }
        }
      }
    }
}
