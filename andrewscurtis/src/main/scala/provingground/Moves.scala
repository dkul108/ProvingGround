package provingground.andrewscurtis

import provingground.andrewscurtis.FreeGroups._
import provingground.Collections._
import scala.language.implicitConversions
import Moves._
import AtomicMove._
import provingground._
import FiniteDistribution._

object AtomicMove {
  def actOnFDVertices(mf: AtomicMove, fdVertices: FiniteDistribution[Moves]): FiniteDistribution[Moves] = mf(fdVertices)
  def actOnMoves(mf: AtomicMove): Moves => Option[Moves] = mf.actOnMoves(_)
}

sealed trait AtomicMove extends (Moves => Option[Moves]){
  def apply(pres: Presentation): Option[Presentation]

  def apply(opPres: Option[Presentation]): Option[Presentation] = {
    opPres match {
      case Some(pres) => this.apply(pres)
      case None => None
    }

  }

  def apply(moves: Moves): Option[Moves] = this.actOnMoves(moves)

  def apply(fdVertices: FiniteDistribution[Moves]): FiniteDistribution[Moves] = {
    (fdVertices mapOpt ((mv: Moves) => this(mv))).flatten
  }

  def actOnMoves(moves: Moves): Option[Moves] = Some(toMoves(this) compose moves)

  def actOnPres(fdPres: FiniteDistribution[Presentation]): FiniteDistribution[Presentation] = {
    (fdPres mapOpt ((pres: Presentation) => this(pres))).flatten
  }

  def compose(mf: AtomicMove): Moves = {
    toMoves(this) compose toMoves(mf)
  }

  def toFunc: Presentation => Option[Presentation] = (pres: Presentation) => this(pres)

  def toPlainString = {
    this match {
      case Id() => "Id()"
      case Inv(k) => s"Inv($k)"
      case RtMult(k, l) => s"RtMult($k, $l)"
      case LftMult(k, l) => s"LftMult($k, $l)"
      case Conj(k, l) => s"Conj($k, $l)"
      case _ => "function1"
    }
  }

  def toLatex = {
    this match {
      case Id() => s"$$r \\mapsto r$$"
      case Inv(k) => s"$$r_{$k} \\mapsto \\bar{r_$k}$$"
      case RtMult(k, l) => if(k<l)
                            s"$$(r_{$k}, r_{$l}) \\mapsto (r_{$k}r_{$l}, r_{$l})$$"
                          else
                            s"$$(r_{$l}, r_{$k}) \\mapsto (r_{$l}, r_{$k}r_{$l})$$"

      case LftMult(k, l) => if(k<l)
                            s"$$(r_{$k}, r_{$l}) \\mapsto (r_{$l}r_{$k}, r_{$l})$$"
                          else
                            s"$$(r_{$l}, r_{$k}) \\mapsto (r_{$l}, r_{$l}r_{$k})$$"
      case Conj(k, l) => s"$$r_{$k} \\mapsto \\bar{\\alpha_{$l}}r_{$k}\\alpha_{$l}$$"
      case _ => "function1"
    }
  }
/*
  def toUnicode = {
    val create_subscript = ((x: Char) =>
                                        x match {
                                        case '-' => 0x208b.toChar
                                        case num => (num.toString.toInt + 0x2080).toChar
                                        }
                           )
    val int_to_sub = ((x: Int) => x.toString map create_subscript)
    this match {
      case Id() => "r \u2192 r"
      case Inv(k) => 
        val sub = int_to_sub(k)
        s"r$sub \u2192 \u203er$sub"
      case _ => "function1"
    }
  }
*/
  override def toString = toPlainString
}

case class Id() extends AtomicMove {
  override def apply(pres: Presentation) = Some(pres)
  override def actOnMoves(moves: Moves) = Some(moves)
}

case class Inv(k: Int) extends AtomicMove {
  override def apply(pres: Presentation): Option[Presentation] = {
    if(k>=0 && k<pres.sz)
      Some(pres.inv(k))
    else
      None
  }
}

case class RtMult(k: Int, l: Int) extends AtomicMove {
  override def apply(pres: Presentation): Option[Presentation] = {
    if(k>=0 && l>=0 && k<pres.sz && l<pres.sz)
      Some(pres.rtmult(k,l))
    else
      None
  }
}

case class LftMult(k: Int, l: Int) extends AtomicMove {
  override def apply(pres: Presentation): Option[Presentation] = {
    if(k>=0 && l>=0 && k<pres.sz && l<pres.sz)
      Some(pres.lftmult(k,l))
    else
      None
  }
}

case class Conj(k: Int, l: Int) extends AtomicMove {
  override def apply(pres: Presentation): Option[Presentation] = {
    if(k>=0 && math.abs(l)>0 && k<pres.sz && math.abs(l)<=pres.rank)
      Some(pres.conj(k,l))
    else
      None
  }
}

case class Moves(moves: List[AtomicMove]) {
  /*
  def reduce: Presentation => Option[Presentation] = {
    if(moves.isEmpty)
      (pres: Presentation) => Some(pres)
    else {
      val f = (x: Presentation => Option[Presentation], y: Presentation => Option[Presentation]) => liftOption(x) compose y
      (moves map ((mf: AtomicMove) => mf.toFunc)) reduce f
    }
  }
*/
  def reduce : Presentation => Option[Presentation] = (pres) => {
    def act(mv: AtomicMove, op: Option[Presentation]) = {
      op flatMap ((p) => mv(p))
      }
    (moves :\ (Some(pres) : Option[Presentation]))(act)
    }

  def apply(pres: Presentation) = this.reduce(pres)
  def apply(that: Moves) = this compose that
  def apply(that: Presentation => Option[Presentation]) = liftOption(this.reduce) compose that
  def apply(that: AtomicMove) = this compose that

  def length = moves.length

  def compose(that: Moves): Moves = {
    Moves(this.moves ++ that.moves)
  }

  def actOnTriv(rank: Int) = this(Presentation.trivial(rank))
}

object Moves {
  def empty = Moves(List.empty)

  implicit def toMoves(move: AtomicMove): Moves = Moves(List(move))

  def liftOption[A](f: A => Option[A]): Option[A] => Option[A] = {
    def lifted_f(a: Option[A]): Option[A] = {
      if(a.isDefined)
        f(a.get)
      else
        None
    }
    lifted_f
  }

  def liftResult[A](f: A => A): A => Option[A] = {
    (a: A) => Some(f(a))
  }

  def actOnTriv(rank: Int)(mvs: Moves) = mvs.actOnTriv(rank)
}
