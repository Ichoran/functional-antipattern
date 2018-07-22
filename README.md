# Functional Antipatterns

Programming is a deeply unnatural task for social savannah hunter-gathering primates.  This
doesn't stop us--we do a lot of unnatural things that turn out to be supremely useful, such
as mathematical physics--but our abilities are enhanced when we can use our tools to
compensate for our most egregious shortcomings.

Functional programming is often touted as a way to compensate for our inability to reason
about sprawling codebases where a change anywhere could impact the workings of things anywhere
else.  It's entirely true that while computers are pretty good at keeping track of all that,
humans are lousy.  Some, like me, are _really_ lousy; some monkey-patch Python and can manage
remarkably well.  (Compared to a computer, everyone is incredibly lousy, though.)

## How this thing works

I have examples below of what I consider to be anti-patterns in functional programming,
along with patterns that are usually superior, which may or may not be functional.  If
you think you have a pattern that solves the problem, let me know (even submit a PR!).
I will almost surely at least acknowledge the point, even if I don't outright accept the edit.

## Background

Fabio Labella made two wonderfully clear posts ([here](https://www.reddit.com/r/scala/comments/8ygjcq/can_someone_explain_to_me_the_benefits_of_io/e2jfp9b/),
[here](https://www.reddit.com/r/scala/comments/8ygjcq/can_someone_explain_to_me_the_benefits_of_io/e2jfrg8/))
on Reddit explaining what the advantages of (pure) FP are in general, and what the advantages
of IO are in particular.  I think that they're very much on-target.  But I think that you also
give up something in order to get those advantages (at least in any extant programming language,
and sometimes especially in Scala which is my lanuage of choice).  Thus it is entirely reasonable
to ask: did I give up too much?  Is this still a case of computers making things easier for us at things
we're bad at, or are they helping with something we're _not_ bad at while sacrificing
something we're good at?  (Note: people do not have identical strengths, so in some cases the
answers may depend on the individual.)

## The essential case for Referential Transparency

Fabio illustrates that the benefits of IO are exactly in enforcing referential
transparency, and then quotes Rob Norris regarding the next essential question:

> What are the benefits of referential transparency

First, a quick aside: what _is_ referential transparency?  In the simplest form,
it is that an expression can be replaced by its value without changing the meaning.
For instance, suppose `x` is a number.  Then `abs(x)` retains referential transparency;
as an example, both of these code snippets return the exact same value:

```scala
def foo(a: Int, b: Int) = abs(a)*(b - abs(a))

def bar(a: Int, b: Int) = {
  val x = abs(a)
  x*(b - x)
}
```

This is not true if we replace `abs(a)` by `scala.util.Random.nextInt(a)`, however.

In essense, with `Random.nextInt(a)` we have lost the ability to reason locally.
You have to go over to `scala.util.Random.nextInt` to ask abouts its properties in
order to know how to  interpret whether `foo` and `bar` do the same thing.  With referential
transparency, you don't have to: if there's a purely syntactic transformation between the two,
then they do the same thing.  Syntactic variants are irrelevant to function.

Fabio lists six simplifying consequences of having referential transparency:

1. Compositionality when understanding code (understand the whole by understanding its parts)
2. Compositionality when assembling code (if pieces work separately, they will work together)
3. Inversion of control (the caller can discard some I/O if it wants--nothing's happened yet)
4. Facile deduplication (if you ever see duplicated code, you can always pull it out to a `val` or `def`)
5. Separating evaluation order from execution order (with IO specifically)
6. Call graphs delimit the region of state sharing (so you're less likely to be confused about when state is shared)

That's pretty nice, honestly.  It's just not always free.

## An outline of this project

In this repository, I intend to collect a variety of examples showing how the quest for referential
transparency can lead one well away from the sweet spot where computers are helping us out.

There are (so far) four general scenarios where I think that referential transparency has drawbacks that are
not worth the advantages.  In some cases, the drawback is only for a particular approach to functional
programming; in others, I cannot envision any way around and/or there are no examples of a way around it.

### Simplification by allowing non-locality

Allowing all pieces of an expression to be context-free and thus locally understood is not
the only way to reduce cognitive burden.  An alternative, sometimes superior strategy
is to _embrace_ context (we do this incessantly in everyday life).  Below will be some
examples where embracing context-sensitivity enables a simplicity that you cannot achieve
with referential transparency.

_Examples to come._

### The morass of monadic nesting

Complex code can end up having lots of different properties for which a wide variety
of different monads are typically used.  This can turn into an unwieldy hairball quite
quickly.  Below will be some examples where using monads for the critical easily-messed-up
operations results in more readily apparent correctness than enforcing referential transparency.

_Examples to come._

### State interactions that can be made autonomous

State interactions can be very complex; one way to deal with them is to be hyper-aware of
them, but it is also possible to allow the state interaction and changes to run autonomously
when you don't need to be aware of the details in order to achieve correct results or have
the desired properties.  The examples below are of this nature.

_Examples to come._

### The problem is inherently linear/affine

_Description to come--I haven't quite thought through a complete example, so this category
may disappear in the future._
