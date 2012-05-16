package org.casualmiracles.finance.contracts

import Stream.Empty

object Contracts {
  type TimeStep = Int
  type CalendarTime = Unit

  type RV[A] = Stream[A]

  case class Observable[T](f: Date ⇒ PR[T]) {
    override def toString = "Observable " + f(time0) + ")"
  }

  case class PR[A](unPr: Stream[RV[A]])

  case class Date(c: CalendarTime, t: TimeStep) extends Ordered[Date] {
    def compare(that: Date) = t.compare(that.t)
  }

  implicit def toConstant[T](x: T) = constant(x)

  def constant[T](k: T): Observable[T] = Observable((d: Date) ⇒ bigK(k))

  def date: Observable[Date] = Observable((t: Date) ⇒ PR(timeSlices(Stream(t))))

  def bigK[T](x: T): PR[T] = PR(konstSlices(x))

  def konstSlices[T](x: T): Stream[RV[T]] = {
    def nextSlice(sl: Stream[T]): Stream[RV[T]] = sl #:: nextSlice(x #:: sl)
    nextSlice(Stream(x))
  }

  def timeSlices(sl: RV[Date]): Stream[RV[Date]] = {
    val (Date(s, t) #:: _) = sl
    val nextSlice = Stream.fill(t + 2)(Date(s, t + 1))
    sl #:: timeSlices(nextSlice)
  }

  implicit def ObservableOps[T <% Double](obs: Observable[T]) = new {
    def *(a: Observable[T]) = lift2((_: T) * (_: T), obs, a)
    def /(a: Observable[T]) = lift2((_: T) / (_: T), obs, a)
    def +(a: Observable[T]) = lift2((_: T) + (_: T), obs, a)
    def -(a: Observable[T]) = lift2((_: T) - (_: T), obs, a)
    def %(a: Observable[T]) = lift2((_: T) % (_: T), obs, a)
  }

  implicit def ObservableRelations[T <% Ordered[T]](obs: Observable[T]) = new {
    def %<(a: Observable[T]) = lift2((_: T) < (_: T), obs, a)
    def %<=(a: Observable[T]) = lift2((_: T) <= (_: T), obs, a)
    def %>(a: Observable[T]) = lift2((_: T) > (_: T), obs, a)
    def %>=(a: Observable[T]) = lift2((_: T) >= (_: T), obs, a)
    def %==(a: Observable[T]) = lift2((_: T) == (_: T), obs, a)
  }

  implicit def ObservableBooleans(obs: Observable[Boolean]) = new {
    def %&&(a: Observable[Boolean]) = lift2((_: Boolean) && (_: Boolean), obs, a)
    def %||(a: Observable[Boolean]) = lift2((_: Boolean) || (_: Boolean), obs, a)
  }

  implicit def ObservableDateOps(obs: Observable[Date]) = new {
    def -(a: Observable[Date]) = lift2((_: Date).t - (_: Date).t, obs, a)
    def +(a: Observable[Date]) = lift2((_: Date).t + (_: Date).t, obs, a)
  }

  def between(d1: Date, d2: Date): Observable[Boolean] = (date %>= d1) %&& (date %<= d2)

  def zipWith[A, B, C](sA: Stream[A], sB: Stream[B])(f: (A, B) ⇒ C): Stream[C] = sA.zip(sB).map(x ⇒ f(x._1, x._2))

  def zipWith3[A, B, C, D](sA: Stream[A], sB: Stream[B], sC: Stream[C])(f: (A, B, C) ⇒ D): Stream[D] = sA.zip(sB.zip(sC)).map(x ⇒ f(x._1, x._2._1, x._2._2))

  def lift[A, B](f: A ⇒ B, obs: Observable[A]): Observable[B] = Observable((t: Date) ⇒ PR(obs.f(t).unPr.map(_.map(f(_)))))

  def lift2[A, B, C](f: (A, B) ⇒ C, obsA: Observable[A], obsB: Observable[B]): Observable[C] = {
    val rvF = (rvA: RV[A], rvB: RV[B]) ⇒ zipWith(rvA, rvB)(f(_, _))
    Observable((t: Date) ⇒ PR(zipWith(obsA.f(t).unPr, obsB.f(t).unPr)(rvF)))
  }

  def one = One.apply _
  def when = (When.apply _).curried
  def anytime = (Anytime.apply _).curried
  def until = (Until.apply _).curried
  def scale = (Scale.apply _).curried
  def cond = (Cond.apply _).curried
  def at(d: Date): Observable[Boolean] = date %== d

  implicit def withEnrichment(c: Contract) = new {
    def and(c2: Contract) = And(c, c2)
    def andGive(c2: Contract) = And(c, Give(c2))
    def or(c2: Contract) = Or(c, c2)
  }

  def mkDate(t: TimeStep): Date = Date((), t)

  def time0: Date = mkDate(0)

  def max[T <% Double](pra: PR[T], prb: PR[T]): PR[Double] = lift2Pr((a: T, b: T) ⇒ math.max(a, b), pra, prb)

  def condPr[T](aPr: PR[Boolean], bPr: PR[T], cPr: PR[T]): PR[T] = lift3Pr((b: Boolean, tru: T, fal: T) ⇒ if (b) tru else fal, aPr, bPr, cPr)

  def liftPr[A, B](f: A ⇒ B, pr: PR[A]): PR[B] = PR(pr.unPr.map(_.map(f(_))))

  def lift2Pr[A, B, C](f: (A, B) ⇒ C, aPr: PR[A], bPr: PR[B]): PR[C] = {
    val rvF = (rvA: RV[A], rvB: RV[B]) ⇒ zipWith(rvA, rvB)(f(_, _))
    PR(zipWith(aPr.unPr, bPr.unPr)(rvF))
  }

  def lift2PrAll[A](f: (A, A) ⇒ A, aPr: PR[A], bPr: PR[A]): PR[A] = {
    val rvF = (rvA: RV[A], rvB: RV[A]) ⇒ zipWith(rvA, rvB)(f(_, _))
    PR(zipWithAll(rvF, aPr.unPr, bPr.unPr))
  }

  def lift3Pr[A, B, C, D](f: (A, B, C) ⇒ D, aPr: PR[A], bPr: PR[B], cPr: PR[C]): PR[D] = {
    val rvF = (rvA: RV[A], rvB: RV[B], rvC: RV[C]) ⇒ zipWith3(rvA, rvB, rvC)(f(_, _, _))
    PR(zipWith3(aPr.unPr, bPr.unPr, cPr.unPr)(rvF))
  }

  def zipWithAll[A](f: (A, A) ⇒ A, sa: Stream[A], sb: Stream[A]): Stream[A] = (sa, sb) match {
    case (a #:: as, b #:: bs) ⇒ f(a, b) #:: zipWithAll(f, as, bs)
    case (as, Empty)          ⇒ as
    case (Empty, bs)          ⇒ bs
    case (_, _)               ⇒ Empty
  }

  implicit def PrOps(prA: PR[Double]) = new {
    def +(prB: PR[Double]): PR[Double] = lift2PrAll((_: Double) + (_: Double), prA, prB)
    def -(prB: PR[Double]): PR[Double] = lift2PrAll((_: Double) - (_: Double), prA, prB)
    def *(prB: PR[Double]): PR[Double] = lift2PrAll((_: Double) * (_: Double), prA, prB)
    def abs: PR[Double] = liftPr((d: Double) ⇒ math.abs(d), prA)
    def signum: PR[Double] = liftPr((d: Double) ⇒ math.signum(d), prA)
  }

  def printPr(pr: PR[_], n: Int) = pr.unPr.take(n).zipWithIndex.foreach { is ⇒ { print(is._2 + ": "); printRV(is._1) } }
  def printRV(rv: RV[_]) {
    rv.foreach(s ⇒ print(s + " "))
    println("")
  }
}