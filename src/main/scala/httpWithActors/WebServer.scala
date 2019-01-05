package httpWithActors

import akka.actor.{Actor, ActorSystem, Props, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json.DefaultJsonProtocol._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

object WebServer {

  implicit val bidFormat = jsonFormat2(Bid)
  implicit val bidsFormat = jsonFormat1(Bids)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  val auction = system.actorOf(Props[Auction], "auction")

  val route = path("auction") {
    put {
      parameter("bid".as[Int], "user") { (bid, user) =>
        auction ! Bid(user, bid)
        complete((StatusCodes.Accepted, "bid placed"))
      }
    } ~
      get {
      implicit val timeout: Timeout = 5.seconds

      // query the actor for the current auction state
      val bids: Future[Bids] = (auction ? GetBids).mapTo[Bids]
      complete(bids)
    }

  }
}

class Auction extends Actor with ActorLogging {
  var bids = List.empty[Bid]

  def receive: PartialFunction[Any, Unit] = {
    case bid@Bid(userId, offer) =>
      bids = bids :+ bid
      log.info(s"Bid complete: $userId, $offer")
    case GetBids => sender() ! Bids(bids)
    case _ => log.info("Invalid message")
  }
}

final case class Bid(userId: String, offer: Int)

case object GetBids

final case class Bids(bids: List[Bid])