package sample.cluster.factorial

import java.net.NetworkInterface
import java.net.InetAddress

import scala.collection.JavaConversions._
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

object NetworkConfig {

  def hostLocalAddress: String = NetworkInterface.getNetworkInterfaces.
        find(_.getName equals "eth0").
        flatMap(interface =>
          interface.getInetAddresses.find(_.isSiteLocalAddress).map(_.getHostAddress)).
        getOrElse("127.0.0.1")
}
