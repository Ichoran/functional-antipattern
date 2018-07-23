# The Random-as-State Antipatterns

Random numbers are frequently used as an example of the `State` monad.  Generating random
numbers functionally without a monad is a terrible idea: you open up a risk
of doing the worst thing you can do with random numbers, while simultaneously making
them slower and more difficult to use.  As far as FP antipatterns go, this is as close
to a disaster as it gets.  `State` helps you avoid reuse, but it still isn't as foolproof
and easy as stateful random numbers.

In _rare_ cases, where you both care very deeply about the precise stream of numbers
_and_ it is very difficult to inspect how the stream is being consumed, `State` is
sort of okay.  Otherwise, the only reason to use referentially transparent random
numbers is if literally everything else in your program is referentially transparent
and you want the simplicity of not ever having to consider that something might not
be, and you're willing to put up with the dangers and hassles.

## Background: The State monad for Random Numbers

_TODO: link to What Is a Monad (after writing it)?_

The `State` monad is conceptually very simple: it takes some representation of a state, and from
it produces an output value and a new state.  That sounds incredibly much like a typical
pseudorandom number generator.  We'll use the `cats` implementation of `State`, but let's
examine a bare-bones version of `State` (which is monadic in what it produces, not the state
that it carries):

```scala
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
```

This isn't super-useful alone, because we can't observe anything that happens, or provide
any initial state, but it structurally _is_ a monad.  To make it useful, we need two
more things, namely a way to start off with an initial state, and a way to eventually do
something with the result of the computation.

So we'll switch over to the implementation given in the
[cats documentation](https://github.com/typelevel/cats/blob/master/docs/src/main/tut/datatypes/state.md).

We can simply:

```scala
import cats.data.State
type RngState[A] = State[Long, A]
val nextLong: RngState[Long] = State(seed =>
  (seed * 6364136223846793005L + 1442695040888963407L, seed)
)
```

(Those numbers are suggested by Knuth for a 64-bit linear congruential generator.)

Let's compare to the stateful version:

```scala
class Rng(private[this] var seed: Long) {
  def nextLong = {
    val ans = seed
    seed = seed * 6364136223846793005L + 1442695040888963407L
    ans
  }
}
```

Since we got `State` for free, the FP version is easier, assuming we're willing to leave our state as a
bare `Long`.  It's important to understand the API of Rng: whenever you want a new random number,
call `nextLong`; it will take care of it.  If you want to reuse a number, it's your responsibility to store
it.  Not too hard to understand, but if you don't understand it you can't reason correctly.

## The problems begin: performance

Let's suppose we want to generate the sum of three random numbers starting with a seed of `17`.  This should be easy enough:

```scala
def sum3mut(r: Rng) = r.nextLong + r.nextLong + r.nextLong
sum3mut(new Rng(17))

val sum3fp = for { a <- nextLong; b <- nextLong; c <- nextLong } yield (a + b + c)
sum3fp.runA(17).value
```

Both seem pretty straightforward.  The syntax is a bit different (direct addition vs. marshalling the
values inside `for` and then yielding the result), but with a little familiaritiy either way is
pretty straightforwards.  Let's try running it on 1,000 initial seeds.
(I'm using my fast-and-only-moderately-accurate benchmarking library Thyme.)

```
Benchmark comparison (in 2.509 s)
Significantly different (p ~= 0)
  Time ratio:    513.69305   95% CI 494.09265 - 533.29345   (n=45)
    First     682.6 ns   95% CI 664.1 ns - 701.0 ns
    Second    350.6 us   95% CI 341.2 us - 360.1 us
```

If you think that you're reading it wrong because it can't possibly be 500x slower,
no, you're reading it right.  That's a 500x performance penalty.  If you need a couple
random numbers, maybe you don't care.  If you're doing, say, Monte Carlo estimation of
error intervals in data, this is pretty awful--the difference between happily running
on one core on your laptop and requiring a giant Spark cluster.

This enormous speed penalty isn't entirely a consequence of FP, but it _is_ a consequence on the
JVM for now, given the complexity of the optimization required to turn the above into something
that performs well.

### Aside--bare `s => (s, a)` is a distaster

Sometimes the monadic trappings about a simple function get in the way.  Not so with random numbers!

We might try to do away with the whole `State` machinery and, perhaps for somewhat
better performance, simply write a plain old function (pof)

```scala
val nextLong = (l: Long) => (l * 6364136223846793005L + 1442695040888963407L, l)
```

This is an unmitigated disaster.  Let's try to add three numbers:

```scala
val sum3pof_wrong = (l: Long) => {
  val (s, a) = nextLong(l)
  val (ss, b) = nextLong(s)
  val (sss, c) = nextLong(s)
  (sss, a + b + c)
}
```

But this is _terrible_ because we exposed ourselves to accidentally reusing seeds
as in this buggy example (the third call should have been `nextLong(ss)`).

Even worse, since we're using random numbers where the number stream and the seed
have the same type, without extra machinery you can even get the order of arguments wrong:

```scala
val sum3pof_verywrong = (l: Long) => {
  val (a, s) = nextLong(l)
  val (b, ss) = nextLong(s)
  val (c, sss) = nextLong(ss)
  (a + b + c, sss)
}
```

Now we've mixed up our seed and our computation, and we generated and added the
same number three times all because the local context did not make it apparent
which of the two results was which.  (You can avoid this by wrapping the seed
to make it unambiguously different than the random number return type.  But
there's no point, because the exposed states are _still_ error-prone in the
first way; we just shouldn't do this at all.)

For random numbers, where independence is (usually) everything, this is as bad
as it gets.  Avoid this pattern like the plague!  If you must have referential
transparency in your random numbers, use `State` or some other mechanism to
enforce the discipline required to keep your state safely progressing!

## Near-parity again: accumulation

Let's suppose we want to generate more than just `Long` numbers.  Maybe we want to generate a string
containing a random sequence of `t` and `f`.  Let's give it a go each way.

First, for our mutable RNG, since we didn't bother to give `nextBool` and `nextString(length: Int)` methods, let's add them.

We have a `fill` method that lets us repeat an operation a certain number of times, so we can:

```scala
implicit class MoreRandomStuff(private val r: Rng) extends AnyVal {
  def nextBool = r.nextLong < 0
  def nextTF = if (nextBool) 't' else 'f'
  def nextString(length: Int) = new String(Array.fill(length)(nextTF))
}
```

Not hard at all!

Let's try with the monadic version.  We can't just use a `for` comprehension, because it doesn't support
generating arbitrarily nested calls to `flatMap`.  Instead, we can turn to a method called `replicateA` that
does something similar (the `A` part meaning that you're replicating just the output, not the seeds)

The trick with `sequence` is that it will turn `F[G[A]]` into `G[F[A]]`, that is swap the wrapper order, assuming that
`G` and `F` are compatible.  It turns out that `State` confuses type inference, but we can explicitly ask for what we
want, and then it works.  Also, because we're swapping two monads, we need to build up all the operations we want inside
the same kind of collection we want to end up with.


```scala
import cats.implicits._
val nextBool = nextLong.map(_ < 0)
val nextTF = nextBool.map(b => if (b) 't' else 'f')
def nextString(length: Int) = nextTF.replicateA(length).map(_.mkString)
```

This wasn't terribly arduous, but it also wasn't free.  We needed an extra concept (`replicateA`--just because we
have `replicateA` doesn't mean we can forget about `fill`!).

Note that referential transparency is only buying us that we do not have to understand the
extremely simple rules for using `Rng`; and we're having to use a wider range of mechanics
to compensate.  Here', it's not a big deal, once you get used to it, but it's not helping
us out.

## Crisis: don't cross the streams!

Suppose we want to create some data structure using these two sources of random numbers.
We won't care about why right now (maybe reproducibility, maybe to get a larger effective
period, maybe one is faster but the other is more random, etc.).  Specifically, we
want to use different random number streams to create an ID and a string of `"t"`s and `"f"`s.

```scala
case class Foo(id: Long, tfs: String) {}
```

Let's say that it's super-important that we don't mess up which stream we use for
what.  We can create some marker traits and adorn our instances with them:

```scala
trait IdR {}
trait TfR {}

val r = new Rng(17) with IdR {}
val rr = new Rng(22) with TfR {}
```

Now we can safely specify in our API which number stream we are using:

```scala
def mkFooMut(rId: Rng with IdR, rTf: Rng with TfR) =
  Foo(rId.nextLong, rTf.nextString(10))

val myFooMut = mkFooMut(r, rr)  // Works!
// val wrongFoo = mkFooMut(rr, r)  // Nope
// val wrongFoo = mkFooMut(new Rng(7), rr)   // Nope
```

Easy type-safety, and all our previous work on random numbers can be reused.  We
could go even farther to avoid error (e.g. here we can still call `rId.nextString(10)`;
the types only keep the arguments straight coming into `mkFoo`), but we'll say this
provides enough security.

Now let's try the same with `RngState`.  Right off the bat, we have a problem: we
need something new to store the state.  There are a bunch of ways we could do this,
but since we're trying to reuse our existing infrastructure, we'll store our
combined state in a tuple and create `State` transformations to deal with each arm:

```
type Rng2State[A] = State[(Long, Long), A]

def nextFromOne[A](r: RngState[A]): Rng2State[A] = State{ s =>
  val (nx, a) = r.run(s._1).value
  (s.copy(_1 = nx), a)
}

def nextFromTwo[A](r: RngState[A]): Rng2State[A] = State { s =>
  val (nx, a) = r.run(s._2).value
  (s.copy(_2 = nx), a)
}
```

Now we can create `Foo` given appropriate state:

```
val mkFoo =
  for {
    id <- nextFromOne(nextLong)
    tfs <- nextFromTwo(nextString(10))
  } yield Foo(id, tfs)

val myFooFp = mkFooFp.runA((17, 22)).value
```

_Except_ for referential transparency, this is unqualifiedly worse.  Not only is there considerably more
boilerplate that we have to write to thread the two states into individual states and back, but we have
the fragile operation of exposing ourself to the random number state and then safely putting it back again.

It's not impossible, and since the area where we have to be very careful is very small, we can probably
manage it.  But our tasks continue to get harder by insisting on referential transparency.

Now suppose we want to keep building up, creating these:

```
case class Bar(id: Long, b: Byte, z: Boolean) {}

case class Baz(id: Long, foo: Foo, bar: Bar) {}
```

But now suppose we want to use yet another source of random numbers that is
the only one that can produce `Byte`s, and which we also will use to make booleans.

```
trait Rn3 extends Rng {
  def nextByte = (nextLong >>> 56).toByte
}

val rrr = new Rng(37) with Rn3 {}
```

No problem.

And now we just create the things we want taking the random number streams we ought to:

```
def mkBarMut(rId: Rng with IdR, rB: Rn3) =
  Bar(rId.nextLong, rB.nextByte, rB.nextBool)

def mkBazMut(rId: Rng with IdR, rTf: Rng with TfR, rB: Rn3) =
  Baz(rId.nextLong, mkFooMut(rId, rTf), mkBarMut(rId, rB))
```

There's one short obvious way to do it, and any simple error is a compile error.  (With the proviso, again,
that we haven't made the streams only able to produce the data type we want.)

I'm going to post the `State` version without describing it blow-by-blow.

```
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

val mkBazFp =
  for {
    id <- nextFromOneOf3(nextLong)
    foo <- nextFromFooish(mkFooFp)
    bar <- nextFromBarish(mkBarFp)
  } yield Baz(id, foo, bar)
```

Whew!  It works, but to keep it even this short we've given up some parts of the
type-safety of the streams (everyone can create `Byte` now), we've got growing
amounts of fiddly state packing and unpacking, and we're increasingly repeating
ourselves in `nextWhatever(makeWhatever)` pairs.

And what do we gain for all this?  Only that we allow ourselves to forget the
distinction between producing and storing a value, and that `Rng` does the former.

## Debriefing

`State` is a clumsy, unscalable mechanism for carrying state along with a
computation, but it does royally well at actually enforcing that the state is
carried along.

In the case of random numbers, the problem is solved _autonomously_ by a mutable
class that takes care of the state on its own.  Not all state problems are like
this.  But many, including random number generation, are.  This separability,
the ability to delegate authority for the process to a class who knows (locally)
how to do the right thing is a very powerful abstraction mechanism.

The desire for referential transparency actually _breaks_ this abstraction
mechanism by forcing you to care, repeatedly, about just how you are propagating
your state around.

When your classes can do it for you, mutably, it's often a vastly simpler
paradigm to use when you have lots of autonomous state.  And random numbers
are exactly this kind of case.

Bottom line: using `State` for random number generation is (usually) an
antipattern.  The costs are high.  If you're going to deal with random
numbers this way, make sure the costs are worth it.
