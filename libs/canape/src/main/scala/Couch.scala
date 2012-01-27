package net.rfc1149.canape

import java.net.{InetSocketAddress, URI}
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.base64.Base64
import org.jboss.netty.handler.queue.BlockingReadHandler
import org.jboss.netty.util.CharsetUtil

/**
 * Connexion to a CouchDB server.
 *
 * @param host the server host name or IP address
 * @param port the server port
 * @param auth an optional (login, password) pair
 */

abstract class Couch(val host: String,
		     val port: Int,
		     private val auth: Option[(String, String)]) extends HTTPBootstrap {

  import implicits._

  def connect(allowChunks: Boolean = false): ChannelFuture = {
    val future = bootstrap.connect(new InetSocketAddress(host, port))
    if (!allowChunks)
      future.getChannel.getPipeline.addLast("aggregator", new Couch.ChunkAggregator(1024*1024))
    future
  }

  private lazy val authorization = {
    val authChannelBuffer = ChannelBuffers.copiedBuffer(auth.get._1 + ":" + auth.get._2,
							CharsetUtil.UTF_8)
    val encodedAuthChannelBuffer = Base64.encode(authChannelBuffer)
    "Basic " + encodedAuthChannelBuffer.toString(CharsetUtil.UTF_8)
  }

  def execute[T: Manifest](request: HttpRequest): T = {
    import implicits._
    val reader = new BlockingReadHandler[Either[Throwable, T]]
    val future = connect(false)
    future.await
    if (future.isSuccess) {
      request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
      val channel = future.getChannel
      channel.getPipeline.addLast("requestInterceptor", new RequestInterceptor)
      channel.getPipeline.addLast("jsonDecoder", new JsonDecoder[T])
      channel.getPipeline.addLast("reader", reader)
      channel.write(request)
      reader.read().fold(throw _, { t: T => t })
    } else
      throw future.getCause
  }

  private[this] def makeRequest[T: Manifest](query: String, method: HttpMethod, data: Option[AnyRef]): CouchRequest[T] = {
    val request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
					 method,
					 "/" + query)
    request.setHeader(HttpHeaders.Names.HOST, host)
    request.setHeader(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP)
    request.setHeader(HttpHeaders.Names.ACCEPT, "application/json")
    auth foreach { case (login, password) =>
      request.setHeader(HttpHeaders.Names.AUTHORIZATION, authorization)
    }
    data foreach { d =>
      val cb = ChannelBuffers.copiedBuffer(if (d == null) "" else write(d), CharsetUtil.UTF_8)
      request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, cb.readableBytes())
      request.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json")
      request.setContent(cb)
    }
    new SimpleCouchRequest[T](this, request)
  }

  def makeGetRequest[T: Manifest](query: String): CouchRequest[T] =
    makeRequest[T](query, HttpMethod.GET, None)

  def makePostRequest[T: Manifest](query: String, data: AnyRef): CouchRequest[T] =
    makeRequest[T](query, HttpMethod.POST, Some(data))

  def makePutRequest[T: Manifest](query: String, data: Option[AnyRef]): CouchRequest[T] =
    makeRequest[T](query, HttpMethod.PUT, data orElse Some(null))

  def makeDeleteRequest[T: Manifest](query: String): CouchRequest[T] =
    makeRequest[T](query, HttpMethod.DELETE, None)

  /** URI that refers to the database */
  private[canape] val uri = "http://" + auth.map(x => x._1 + ":" + x._2 + "@").getOrElse("") + host + ":" + port

  protected def canEqual(that: Any) = that.isInstanceOf[Couch]

  override def equals(that: Any) = that match {
      case other: Couch if other.canEqual(this) => uri == other.uri
      case _                                    => false
  }

  override def hashCode() = toString.hashCode()

  override def toString =
    "http://" + auth.map(x => x._1 + ":********@").getOrElse("") + host + ":" + port

  /**
   * Launch a mono-directional replication.
   *
   * @param source the database to replicate from
   * @param target the database to replicate into
   * @param continuous true if the replication must be continuous, false otherwise
   *
   * @throws StatusCode if an error occurs
   */
  def replicate(source: Database, target: Database, continuous: Boolean): CouchRequest[JValue] = {
    makePostRequest[JValue]("_replicate",
			    Map("source" -> source.uriFrom(this),
				"target" -> target.uriFrom(this),
				"continuous" -> continuous))
  }

  /**
   * CouchDB installation status.
   *
   * @return the status as a Handler
   */
  def status(): CouchRequest[Couch.Status] = makeGetRequest[Couch.Status]("")


  /**
   * CouchDB active tasks.
   *
   * @return the list of active tasks as a JSON objects list in a Handler
   * @throws StatusCode if an error occurs
   */
  def activeTasks(): CouchRequest[List[JValue]] = makeGetRequest[List[JValue]]("_active_tasks")

  /**
   * Get a named database. This does not attempt to connect to the database or check
   * its existence.
   *
   * @return an object representing this database.
   */
  def db(databaseName: String) = Database(this, databaseName)

}

object Couch {

  /** The Couch instance current status. */
  case class Status(couchdb: String,
		    version: String,
		    vendor: Option[VendorInfo])

  case class VendorInfo(name: String,
			version: String)

  private class ChunkAggregator(capacity: Int) extends HttpChunkAggregator(capacity) {

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) =
      ctx.sendUpstream(e)

  }

}
