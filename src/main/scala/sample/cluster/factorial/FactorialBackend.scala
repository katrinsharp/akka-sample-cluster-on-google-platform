package sample.cluster.factorial

import scala.annotation.tailrec
import scala.concurrent.Future
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.pipe

//#backend
class FactorialBackend extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {
    case (n: Int) =>
      val from = sender()
      Future(factorial(n)) map { result =>
        log.info("{}! = {} sender: {}", n, result, from.path)
        (n, result)
      } pipeTo from
  }

  def factorial(n: Int): BigInt = {
    @tailrec def factorialAcc(acc: BigInt, n: Int): BigInt = {
      if (n <= 1) acc
      else factorialAcc(acc * n, n - 1)
    }
    factorialAcc(BigInt(1), n)
  }

}
//#backend

object FactorialBackend {

  def main(args: Array[String]): Unit = {

    val port = if (args.isEmpty) "0" else args(0)

    val internalIp = NetworkConfig.hostLocalAddress

    val appConfig = ConfigFactory.load("factorial")
    val clusterName = appConfig.getString("clustering.name")

    val config = ConfigFactory.parseString("akka.cluster.roles = [backend]").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")).
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.bind-hostname=$internalIp")).
      withFallback(appConfig)

    val system = ActorSystem(clusterName, config)
    system.actorOf(Props[FactorialBackend], name = "factorialBackend")
  }
}