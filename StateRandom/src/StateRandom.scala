package ichoran.functionalantipattern

object StateRandom {
  object HypotheticalExample {
      class State[S, A](private val advance: S => (S, A)) {
        def pure[B](b: B) = new State[S, B](s => (s, b))
        def flatMap[B](f: A => State[S, B]): State[S, B] = new State(s => {
          val (ss, a) = advance(s)
          f(a).advance(ss)
        })
      }
      object State {
        def apply[S, A](advance: S => (S, A)) = new State(advance)
      }
  }


  import cats.data.State
  type RngState[A] = State[Long, A]
  val nextLong: RngState[Long] = State(seed =>
    (seed * 6364136223846793005L + 1442695040888963407L, seed)
  )


  class Rng(private[this] var seed: Long) {
    def nextLong = {
      val ans = seed
      seed = seed * 6364136223846793005L + 1442695040888963407L
      ans
    }
  }


  def sum3mut(r: Rng) = r.nextLong + r.nextLong + r.nextLong
  sum3mut(new Rng(17))
  
  val sum3fp = for { a <- nextLong; b <- nextLong; c <- nextLong } yield (a + b + c)
  sum3fp.runA(17).value

  object Disaster {
    val nextLong = (l: Long) => (l * 6364136223846793005L + 1442695040888963407L, l)
 
    val sum3pof_wrong = (l: Long) => {
      val (s, a) = nextLong(l)
      val (ss, b) = nextLong(s)
      val (sss, c) = nextLong(s)
      (sss, a + b + c)
    }

    val sum3pof_verywrong = (l: Long) => {
      val (a, s) = nextLong(l)
      val (b, ss) = nextLong(s)
      val (c, sss) = nextLong(ss)
      (a + b + c, sss)
    }
  }

  implicit class MoreRandomStuff(private val r: Rng) extends AnyVal {
    def nextBool = r.nextLong < 0
    def nextTF = if (nextBool) 't' else 'f'
    def nextString(length: Int) = new String(Array.fill(length)(nextTF))
  }
 
  import cats.implicits._
  val nextBool = nextLong.map(_ < 0)
  val nextTF = nextBool.map(b => if (b) 't' else 'f')
  def nextString(length: Int) = nextTF.replicateA(length).map(_.mkString)


  case class Foo(id: Long, tfs: String) {}
  
  trait IdR {}
  trait TfR {}
  
  val r = new Rng(17) with IdR {}
  val rr = new Rng(22) with TfR {}
  
  def mkFooMut(rId: Rng with IdR, rTf: Rng with TfR) =
    Foo(rId.nextLong, rTf.nextString(10))
  
  val myFooMut = mkFooMut(r, rr)  // Works!


  type Rng2State[A] = State[(Long, Long), A]
  
  def nextFromOne[A](r: RngState[A]): Rng2State[A] = State{ s =>
    val (nx, a) = r.run(s._1).value
    (s.copy(_1 = nx), a)
  }
  
  def nextFromTwo[A](r: RngState[A]): Rng2State[A] = State { s =>
    val (nx, a) = r.run(s._2).value
    (s.copy(_2 = nx), a)
  }


  val mkFooFp =
    for {
      id <- nextFromOne(nextLong)
      tfs <- nextFromTwo(nextString(10))
    } yield Foo(id, tfs)
  
  val myFooFp = mkFooFp.runA((17, 22)).value


  case class Bar(id: Long, b: Byte, z: Boolean) {}
  
  case class Baz(id: Long, foo: Foo, bar: Bar) {}


  trait Rn3 extends Rng {
    def nextByte = (nextLong >>> 56).toByte
  }
  
  val rrr = new Rng(37) with Rn3 {}

  def mkBarMut(rId: Rng with IdR, rB: Rn3) =
    Bar(rId.nextLong, rB.nextByte, rB.nextBool)
  
  def mkBazMut(rId: Rng with IdR, rTf: Rng with TfR, rB: Rn3) =
    Baz(rId.nextLong, mkFooMut(rId, rTf), mkBarMut(rId, rB))


  type Rng3State[A] = State[(Long, Long, Long), A]
  
  def nextFromOneOf3[A](r: RngState[A]): Rng3State[A] = State{ s =>
    val (sid2, a) = r.run(s._1).value
    (s.copy(_1 = sid2), a)
  }
  
  def nextFromFooish[A](r2: Rng2State[A]): Rng3State[A] = State{ s =>
    val (nx, a) = r2.run((s._1, s._2)).value
    (s.copy(_1 = nx._1, _2 = nx._2), a)
  }
  
  def nextFromBarish[A](r2: Rng2State[A]): Rng3State[A] = State{ s =>
    val (nx, a) = r2.run((s._1, s._3)).value
    (s.copy(_1 = nx._1, _3 = nx._2), a)
  }
  
  // We're not even enforcing stream-type-safety here!
  val nextByte = nextLong.map(l => (l >>> 56).toByte)
  
  val mkBarFp =
    for {
      id <- nextFromOne(nextLong)
      b <- nextFromTwo(nextByte)
      z <- nextFromTwo(nextBool)
    } yield Bar(id, b, z)
  
  val mkBaz =
    for {
      id <- nextFromOneOf3(nextLong)
      foo <- nextFromFooish(mkFooFp)
      bar <- nextFromBarish(mkBarFp)
    } yield Baz(id, foo, bar)
}
