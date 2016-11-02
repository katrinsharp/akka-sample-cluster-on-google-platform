package constructr.dns

import java.net.InetAddress

import akka.Done
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.constructr.coordination.Coordination
import de.heikoseeberger.constructr.coordination.Coordination.NodeSerialization

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DnsCoordination
(val prefix: String, val clusterName: String, val system: ActorSystem) extends Coordination {

  val appConfig = system.settings.config
  val isLocal = appConfig.getString("constructr.dns.seed-discovery-service-url").isEmpty
  val numOfSeedNodes = appConfig.getInt("constructr.dns.num-of-seeds")

  override def getNodes[A: NodeSerialization](): Future[Set[A]] = {
    val nodes = if(isLocal)
      appConfig.getStringList("constructr.dns.seed-nodes-local")
        .toList
        .map(_.replace("<cluster-name>", clusterName))
    else {
      val seedNodes = seedNodesIps
      if(numOfSeedNodes == seedNodes.size)
        seedNodes.sortWith(_ < _)
      else Set.empty
    }
    Future.successful(nodes.map(ip => NodeSerialization.fromBytes(ip.getBytes("utf-8"))).toSet)
  }

  override def refresh[A: NodeSerialization](self: A, ttl: FiniteDuration): Future[Done] =
    Future.successful(Done)

  override def lock[A: NodeSerialization](self: A, ttl: FiniteDuration): Future[Boolean] =
    Future.successful(false)

  override def addSelf[A: NodeSerialization](self: A, ttl: FiniteDuration): Future[Done] =
    Future.successful(Done)

  private def seedPort = appConfig.getInt("constructr.dns.seed-port")

  private def seedNodesIps: Seq[String] = {
    val dns = appConfig.getString("constructr.dns.seed-discovery-service-url")
    val akkaProtocol = appConfig.getString("constructr.dns.akka-protocol")
    InetAddress.getAllByName(dns).map(ip => s"$akkaProtocol://$clusterName@${ip.getHostAddress}:$seedPort").toSeq
  }
}
