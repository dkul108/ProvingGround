package provingground.learning

import provingground._, HoTT._
import TypedPostResponse._
import monix.eval._
import HoTTPost._
import monix.execution.Scheduler.Implicits.{global => monixglobal}
import scala.concurrent._

class HoTTPost { web =>
  val global = new CounterGlobalID()

  var equationNodes: Set[EquationNode] = Set()

  def equations = Equation.group(equationNodes)

  def addEqns(eqs: Set[EquationNode]): Unit = {
    equationNodes ++= eqs
  }

  val lpBuff = PostBuffer[LocalProver, ID](global.postGlobal)

  val expEvalBuff = PostBuffer[ExpressionEval, ID](global.postGlobal)

  val eqnNodeBuff = PostBuffer[Set[EquationNode], ID](global.postGlobal)

  val buffers: Vector[PostBuffer[_, ID]] =
    Vector(lpBuff, eqnNodeBuff, expEvalBuff)

}

object HoTTPost {
  type ID = (Int, Int)

  implicit val postLP: Postable[LocalProver, HoTTPost, ID] = {
    def postFunc(lp: LocalProver, web: HoTTPost, ids: Set[ID]): Task[ID] =
      web.lpBuff.post(lp, ids)
    Postable(postFunc, true)
  }

  implicit val postExpEv: Postable[ExpressionEval, HoTTPost, ID] = {
    def postFunc(ev: ExpressionEval, web: HoTTPost, ids: Set[ID]): Task[ID] =
      web.expEvalBuff.post(ev, ids)
    Postable(postFunc, false)
  }

  implicit val postEqnNodes: Postable[Set[EquationNode], HoTTPost, ID] = {
    def postFunc(
        eqns: Set[EquationNode],
        web: HoTTPost,
        ids: Set[ID]
    ): Task[ID] = web.eqnNodeBuff.post(eqns, ids)
    Postable(postFunc, false)
  }

  case class WebBuffer[P](buffer: PostBuffer[P, ID])(
      implicit pw: Postable[P, HoTTPost, ID]
  ) {
    def getPost(id: ID): Option[(PostData[_, HoTTPost, ID], Set[ID])] =
      buffer.find(id) 

    def data : Vector[PostData[_, HoTTPost, ID]] = buffer.bufferData

    def fullData : Vector[(PostData[_,HoTTPost,ID], ID, Set[ID])] = buffer.bufferFullData
  }

  def webBuffers(web: HoTTPost): Vector[WebBuffer[_]] =
    Vector() :+ WebBuffer(web.lpBuff) :+ WebBuffer(web.expEvalBuff) :+ WebBuffer(
      web.eqnNodeBuff
    )

  def findInWeb(web: HoTTPost, index: ID) : Option[(PostData[_, HoTTPost, ID], Set[ID])] =
    webBuffers(web)
      .map(_.getPost(index))
      .fold[Option[(PostData[_, HoTTPost, ID], Set[ID])]](None)(_ orElse _)

  implicit def postHistory: PostHistory[HoTTPost, ID] = new PostHistory[HoTTPost, ID] {
    def history(web: HoTTPost, id: ID): Stream[PostData[_, HoTTPost, ID]] = {
      val next : ((Set[PostData[_, HoTTPost, ID]], Set[ID])) =>  (Set[PostData[_, HoTTPost, ID]], Set[ID])   = {case (d, indices) =>
        val pairs = indices.map(findInWeb(web, _)).flatten
        (pairs.map(_._1), pairs.flatMap(_._2))
      }
      def stream : Stream[(Set[PostData[_, HoTTPost, (Int, Int)]], Set[ID])] = 
        ((Set.empty[PostData[_, HoTTPost, (Int, Int)]], Set(id))) #:: stream.map(next)
      stream.flatMap(_._1)
    }
  }

  def allPosts(web: HoTTPost): Vector[PostData[_, HoTTPost, ID]] = webBuffers(web).flatMap(_.data)

  def allPostFullData(web: HoTTPost) : Vector[(PostData[_, HoTTPost, ID], ID, Set[ID])]= webBuffers(web).flatMap(_.fullData)

  case class HoTTPostData(number: Int, posts: Vector[(PostData[_, HoTTPost, ID], ID, Set[ID])]){
    def successors(id: ID) = posts.filter(_._3.contains(id))

    val allIndices = posts.map(_._2)

    lazy val leafIndices = allIndices.filter(id => successors(id).isEmpty)
  }

  implicit def hottPostDataQuery : Queryable[HoTTPostData, HoTTPost] = new Queryable[HoTTPostData, HoTTPost]{
    def get(web: HoTTPost): Task[HoTTPostData] = Task{
      HoTTPostData(
        web.global.counter,
        allPostFullData(web)
      )
    }
  }

  lazy val lpToExpEv: PostResponse[HoTTPost, ID] = {
    val response: Unit => LocalProver => Task[ExpressionEval] = (_) =>
      lp => lp.expressionEval
    Poster(response)
  }

  lazy val expEvToEqns: PostResponse[HoTTPost, ID] = {
    val response: Unit => ExpressionEval => Task[Set[EquationNode]] = (_) =>
      (expEv) => Task(expEv.equations.flatMap(Equation.split))
    Poster(response)
  }

  lazy val eqnUpdate: PostResponse[HoTTPost, ID] = {
    val update: HoTTPost => Unit => Set[EquationNode] => Task[Unit] = web =>
      (_) => eqns => Task(web.addEqns(eqns))
    Callback(update)
  }

}

class HoTTSession
    extends SimpleSession(
      new HoTTPost(),
      Vector(lpToExpEv, expEvToEqns, eqnUpdate)
    ) {
  // just an illustration, should just use rhs
  def postLocalProverTask(
      lp: LocalProver,
      pred: Set[ID] = Set()
  ): Task[PostData[LocalProver, HoTTPost, HoTTPost.ID]] =
    postTask(lp, pred)

  def postLP(
      lp: LocalProver,
      pred: Set[ID] = Set()
  ): Future[PostData[LocalProver, HoTTPost, HoTTPost.ID]] =
    postLocalProverTask(lp, pred).runToFuture
}