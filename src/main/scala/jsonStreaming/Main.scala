package jsonStreaming

import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Source
import spray.json.DefaultJsonProtocol._

final case class Tweet(uid: Long, txt: String)

object Main extends App {

  implicit val tweetFormat = jsonFormat2(Tweet)

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport =
    EntityStreamingSupport.json()

  val input =
    """{"uid":1,"txt":"#Akka rocks!"}""" + "\n" +
      """{"uid":2,"txt":"Streaming is so hot right now!"}""" + "\n" +
      """{"uid":3,"txt":"You cannot enter the same river twice."}"""

  val response = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, input))

  val value: Source[Tweet, Any] = response.entity.dataBytes
    .via(jsonStreamingSupport.framingDecoder) // pick your Framing (could be "\n" etc)
    .mapAsync(1)(bytes ⇒ Unmarshal(bytes).to[Tweet]) // unmarshal one by one

}
