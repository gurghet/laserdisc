package laserdisc
package protocol

object SortedSetP {
  sealed trait Aggregate
  final object Aggregate {
    final object sum extends Aggregate
    final object min extends Aggregate
    final object max extends Aggregate

    implicit val aggregateShow: Show[Aggregate] = Show.instance {
      case `sum` => "SUM"
      case `min` => "MIN"
      case `max` => "MAX"
    }
  }

  sealed trait Flag
  final object Flag {
    final object nx extends Flag
    final object xx extends Flag

    implicit val flagShow: Show[Flag] = Show.instance {
      case `nx` => "NX"
      case `xx` => "XX"
    }
  }

  sealed trait LexRange { def min: String; def max: String }
  final object LexRange {
    private[this] def open(s: String)  = s"($s"
    private[this] def close(s: String) = s"[$s"
    private[this] final val posInf     = "+"
    private[this] final val negInf     = "-"

    sealed abstract class Open private[LexRange] (
        private[this] val minimum: String,
        private[this] val maximum: String
    ) extends LexRange {
      override final val min: String = open(minimum)
      override final val max: String = open(maximum)
    }
    sealed abstract class OpenClosed private[LexRange] (
        private[this] val minimum: String,
        private[this] val maximum: String
    ) extends LexRange {
      override final val min: String = open(minimum)
      override final val max: String = close(maximum)
    }
    sealed abstract class ClosedOpen private[LexRange] (
        private[this] val minimum: String,
        private[this] val maximum: String
    ) extends LexRange {
      override final val min: String = close(minimum)
      override final val max: String = open(maximum)
    }
    sealed abstract class Closed private[LexRange] (
        private[this] val minimum: String,
        private[this] val maximum: String
    ) extends LexRange {
      override final val min: String = close(minimum)
      override final val max: String = close(maximum)
    }
    sealed abstract class OpenInf private[LexRange] (private[this] val minimum: String) extends LexRange {
      override final val min: String = open(minimum)
      override final val max: String = posInf
    }
    sealed abstract class ClosedInf private[LexRange] (private[this] val minimum: String) extends LexRange {
      override final val min: String = close(minimum)
      override final val max: String = posInf
    }
    sealed abstract class InfOpen private[LexRange] (private[this] val maximum: String) extends LexRange {
      override final val min: String = negInf
      override final val max: String = open(maximum)
    }
    sealed abstract class InfClosed private[LexRange] (private[this] val maximum: String) extends LexRange {
      override final val min: String = negInf
      override final val max: String = close(maximum)
    }

    def open(min: Key, max: Key): LexRange       = new Open(min.value, max.value)       {}
    def openClosed(min: Key, max: Key): LexRange = new OpenClosed(min.value, max.value) {}
    def closedOpen(min: Key, max: Key): LexRange = new ClosedOpen(min.value, max.value) {}
    def closed(min: Key, max: Key): LexRange     = new Closed(min.value, max.value)     {}
    def openInf(min: Key): LexRange              = new OpenInf(min.value)               {}
    def closedInf(min: Key): LexRange            = new ClosedInf(min.value)             {}
    def infOpen(max: Key): LexRange              = new InfOpen(max.value)               {}
    def infClosed(max: Key): LexRange            = new InfClosed(max.value)             {}
  }

  sealed trait ScoreRange { def min: String; def max: String }
  final object ScoreRange {
    private[this] def open(d: ValidDouble)  = s"(${Show.validDoubleShow.show(d)}"
    private[this] def close(d: ValidDouble) = Show.validDoubleShow.show(d)

    sealed abstract class Open private[ScoreRange] (
        private[this] val minimum: ValidDouble,
        private[this] val maximum: ValidDouble
    ) extends ScoreRange {
      override final val min: String = open(minimum)
      override final val max: String = open(maximum)
    }
    sealed abstract class OpenClosed private[ScoreRange] (
        private[this] val minimum: ValidDouble,
        private[this] val maximum: ValidDouble
    ) extends ScoreRange {
      override final val min: String = open(minimum)
      override final val max: String = close(maximum)
    }
    sealed abstract class ClosedOpen private[ScoreRange] (
        private[this] val minimum: ValidDouble,
        private[this] val maximum: ValidDouble
    ) extends ScoreRange {
      override final val min: String = close(minimum)
      override final val max: String = open(maximum)
    }
    sealed abstract class Closed private[ScoreRange] (
        private[this] val minimum: ValidDouble,
        private[this] val maximum: ValidDouble
    ) extends ScoreRange {
      override final val min: String = close(minimum)
      override final val max: String = close(maximum)
    }

    def open(min: ValidDouble, max: ValidDouble): ScoreRange       = new Open(min, max)       {}
    def openClosed(min: ValidDouble, max: ValidDouble): ScoreRange = new OpenClosed(min, max) {}
    def closedOpen(min: ValidDouble, max: ValidDouble): ScoreRange = new ClosedOpen(min, max) {}
    def closed(min: ValidDouble, max: ValidDouble): ScoreRange     = new Closed(min, max)     {}
  }
}

trait SortedSetP {
  import SortedSetP.{Aggregate, Flag, LexRange, ScoreRange}
  import Read.==>
  import auto._
  import shapeless._

  private[this] final val zeroIsNone = RESPRead.instance(Read.integerZeroIsNone[PosInt])

  final object sortedsets {
    final val aggregate  = Aggregate
    final val flag       = Flag
    final val lexrange   = LexRange
    final val scorerange = ScoreRange
  }

  final def zadd[A: Show](key: Key, scoredMembers: OneOrMore[(A, ValidDouble)]): Protocol.Aux[NonNegInt] =
    Protocol("ZADD", key :: scoredMembers.value.map(_.swap) :: HNil).as[Integer, NonNegInt]

  final def zadd[A: Show](key: Key, flag: Flag, scoredMembers: OneOrMore[(A, ValidDouble)]): Protocol.Aux[NonNegInt] =
    Protocol("ZADD", key :: flag :: scoredMembers.map(_.swap) :: HNil).as[Integer, NonNegInt]

  final def zaddch[A: Show](key: Key, scoredMembers: OneOrMore[(A, ValidDouble)]): Protocol.Aux[NonNegInt] =
    Protocol("ZADD", key :: "CH" :: scoredMembers.map(_.swap) :: HNil).as[Integer, NonNegInt]

  final def zaddch[A: Show](key: Key, flag: Flag, scoredMembers: OneOrMore[(A, ValidDouble)]): Protocol.Aux[NonNegInt] =
    Protocol("ZADD", key :: flag :: "CH" :: scoredMembers.map(_.swap) :: HNil).as[Integer, NonNegInt]

  final def zaddincr[A: Show](key: Key, member: A, increment: NonZeroDouble): Protocol.Aux[Double] =
    Protocol("ZADD", key :: increment :: member :: HNil).as[NonNullBulkString, Double]

  final def zaddincr[A: Show](key: Key, flag: Flag, member: A, increment: NonZeroDouble): Protocol.Aux[Double] =
    Protocol("ZADD", key :: flag :: increment :: member :: HNil).as[NonNullBulkString, Double]

  final def zaddchincr[A: Show](key: Key, member: A, increment: NonZeroDouble): Protocol.Aux[Double] =
    Protocol("ZADD", key :: "CH" :: "INCR" :: increment :: member :: HNil).as[NonNullBulkString, Double]

  final def zaddchincr[A: Show](key: Key, flag: Flag, member: A, increment: NonZeroDouble): Protocol.Aux[Double] =
    Protocol("ZADD", key :: flag :: "CH" :: "INCR" :: increment :: member :: HNil).as[NonNullBulkString, Double]

  final def zcard(key: Key): Protocol.Aux[Option[PosInt]] = Protocol("ZCARD", key :: HNil).using(zeroIsNone)

  final def zcount(key: Key, range: ScoreRange): Protocol.Aux[NonNegInt] =
    Protocol("ZCOUNT", key :: range.min :: range.max :: HNil).as[Integer, NonNegInt]

  final def zincrby[A: Show](key: Key, member: A, increment: ValidDouble): Protocol.Aux[Double] =
    Protocol("ZINCRBY", key :: increment :: member :: HNil).as[NonNullBulkString, Double]

  final def zinterstore(keys: TwoOrMoreKeys, destinationKey: Key): Protocol.Aux[NonNegInt] =
    Protocol("ZINTERSTORE", destinationKey :: keys.length :: keys.value :: HNil).as[Integer, NonNegInt]

  final def zinterstoreweighted(weightedKeys: TwoOrMoreWeightedKeys, destinationKey: Key): Protocol.Aux[NonNegInt] = {
    val (keys, weights) = weightedKeys.unzip
    Protocol("ZINTERSTORE", destinationKey :: keys.length :: keys :: "WEIGHTS" :: weights :: HNil)
      .as[Integer, NonNegInt]
  }

  final def zinterstore(keys: TwoOrMoreKeys, destinationKey: Key, aggregate: Aggregate): Protocol.Aux[NonNegInt] =
    Protocol("ZINTERSTORE", destinationKey :: keys.length :: keys.value :: "AGGREGATE" :: aggregate :: HNil)
      .as[Integer, NonNegInt]

  final def zinterstoreweighted(
      weightedKeys: TwoOrMoreWeightedKeys,
      destinationKey: Key,
      aggregate: Aggregate
  ): Protocol.Aux[NonNegInt] = {
    val (keys, weights) = weightedKeys.unzip
    Protocol(
      "ZINTERSTORE",
      destinationKey :: keys.length :: keys :: "WEIGHTS" :: weights :: "AGGREGATE" :: aggregate :: HNil
    ).as[Integer, NonNegInt]
  }

  final def zlexcount(key: Key, range: LexRange): Protocol.Aux[NonNegInt] =
    Protocol("ZLEXCOUNT", key :: range.min :: range.max :: HNil).as[Integer, NonNegInt]

  final def zrange[A](key: Key, start: Index, stop: Index)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] = Protocol("ZRANGE", key :: start :: stop :: HNil).as[NonNilArray, Seq[A]]

  final def zrangebylex[A](key: Key, range: LexRange)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] = Protocol("ZRANGEBYLEX", key :: range.min :: range.max :: HNil).as[NonNilArray, Seq[A]]

  final def zrangebylex[A](key: Key, range: LexRange, offset: NonNegLong, count: PosLong)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] =
    Protocol("ZRANGEBYLEX", key :: range.min :: range.max :: "LIMIT" :: offset :: count :: HNil).as[NonNilArray, Seq[A]]

  final def zrangebyscore[A](key: Key, range: ScoreRange)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] = Protocol("ZRANGEBYSCORE", key :: range.min :: range.max :: HNil).as[NonNilArray, Seq[A]]

  final def zrangebyscore[A](key: Key, range: ScoreRange, offset: NonNegLong, count: PosLong)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] =
    Protocol("ZRANGEBYSCORE", key :: range.min :: range.max :: "LIMIT" :: offset :: count :: HNil)
      .as[NonNilArray, Seq[A]]

  final def zrangebyscorewithscores[A](key: Key, range: ScoreRange)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[(A, Double)]] =
    Protocol("ZRANGEBYSCORE", key :: range.min :: range.max :: "WITHSCORES" :: HNil).as[NonNilArray, Seq[(A, Double)]]

  final def zrangebyscorewithscores[A](key: Key, range: ScoreRange, offset: NonNegLong, count: PosLong)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[(A, Double)]] =
    Protocol("ZRANGEBYSCORE", key :: range.min :: range.max :: "WITHSCORES" :: "LIMIT" :: offset :: count :: HNil)
      .as[NonNilArray, Seq[(A, Double)]]

  final def zrank(key: Key, member: Key): Protocol.Aux[Option[NonNegInt]] =
    Protocol("ZRANK", key :: member :: Nil).asC[NullBulkString :+: Integer :+: CNil, Option[NonNegInt]]

  final def zrem[A: Show](key: Key, members: OneOrMore[A]): Protocol.Aux[NonNegInt] =
    Protocol("ZREM", key :: members.value :: HNil).as[Integer, NonNegInt]

  final def zremrangebylex(key: Key, range: LexRange): Protocol.Aux[NonNegInt] =
    Protocol("ZREMRANGEBYLEX", key :: range.min :: range.max :: HNil).as[Integer, NonNegInt]

  final def zremrangebyrank(key: Key, start: Index, stop: Index): Protocol.Aux[NonNegInt] =
    Protocol("ZREMRANGEBYRANK", key :: start :: stop :: HNil).as[Integer, NonNegInt]

  final def zremrangebyscore(key: Key, range: ScoreRange): Protocol.Aux[NonNegInt] =
    Protocol("ZREMRANGEBYSCORE", key :: range.min :: range.max :: HNil).as[Integer, NonNegInt]

  final def zrevrange[A](key: Key, start: Index, stop: Index)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] = Protocol("ZREVRANGE", key :: start :: stop :: HNil).as[NonNilArray, Seq[A]]

  final def zrevrangebylex[A](key: Key, range: LexRange)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] = Protocol("ZREVRANGEBYLEX", key :: range.max :: range.min :: HNil).as[NonNilArray, Seq[A]]

  final def zrevrangebylex[A](key: Key, range: LexRange, offset: NonNegLong, count: PosLong)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] =
    Protocol("ZREVRANGEBYLEX", key :: range.max :: range.min :: "LIMIT" :: offset :: count :: HNil)
      .as[NonNilArray, Seq[A]]

  final def zrevrangebyscore[A](key: Key, range: ScoreRange)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] = Protocol("ZREVRANGEBYSCORE", key :: range.max :: range.min :: HNil).as[NonNilArray, Seq[A]]

  final def zrevrangebyscore[A](key: Key, range: ScoreRange, offset: NonNegLong, count: PosLong)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[A]] =
    Protocol("ZREVRANGEBYSCORE", key :: range.max :: range.min :: "LIMIT" :: offset :: count :: HNil)
      .as[NonNilArray, Seq[A]]

  final def zrevrangebyscorewithscores[A](key: Key, range: ScoreRange)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[(A, Double)]] =
    Protocol("ZREVRANGEBYSCORE", key :: range.max :: range.min :: "WITHSCORES" :: HNil)
      .as[NonNilArray, Seq[(A, Double)]]

  final def zrevrangebyscorewithscores[A](key: Key, range: ScoreRange, offset: NonNegLong, count: PosLong)(
      implicit ev: NonNullBulkString ==> A
  ): Protocol.Aux[Seq[(A, Double)]] =
    Protocol("ZREVRANGEBYSCORE", key :: range.max :: range.min :: "WITHSCORES" :: "LIMIT" :: offset :: count :: HNil)
      .as[NonNilArray, Seq[(A, Double)]]

  final def zrevrank(key: NonNullBulkString, member: Key): Protocol.Aux[Option[NonNegInt]] =
    Protocol("ZREVRANK", key :: member :: HNil).asC[NullBulkString :+: Integer :+: CNil, Option[NonNegInt]]

  final def zscan[A](key: Key, cursor: NonNegLong)(
      implicit ev: NonNilArray ==> Seq[A]
  ): Protocol.Aux[Scan[A]] = Protocol("ZSCAN", key :: cursor :: HNil).as[NonNilArray, Scan[A]]

  final def zscan[A](key: Key, cursor: NonNegLong, pattern: GlobPattern)(
      implicit ev: NonNilArray ==> Seq[A]
  ): Protocol.Aux[Scan[A]] = Protocol("ZSCAN", key :: cursor :: "MATCH" :: pattern :: HNil).as[NonNilArray, Scan[A]]

  final def zscan[A](key: Key, cursor: NonNegLong, count: PosInt)(
      implicit ev: NonNilArray ==> Seq[A]
  ): Protocol.Aux[Scan[A]] = Protocol("ZSCAN", key :: cursor :: "COUNT" :: count :: HNil).as[NonNilArray, Scan[A]]

  final def zscan[A](key: Key, cursor: NonNegLong, pattern: GlobPattern, count: PosInt)(
      implicit ev: NonNilArray ==> Seq[A]
  ): Protocol.Aux[Scan[A]] =
    Protocol("ZSCAN", key :: cursor :: "MATCH" :: pattern :: "COUNT" :: count :: HNil).as[NonNilArray, Scan[A]]

  final def zscore[A: Show](key: Key, member: A): Protocol.Aux[Option[Double]] =
    Protocol("ZSCORE", key :: member :: HNil).asC[NullBulkString :+: NonNullBulkString :+: CNil, Option[Double]]

  final def zunionstore(keys: TwoOrMoreKeys, destinationKey: Key): Protocol.Aux[NonNegInt] =
    Protocol("ZUNIONSTORE", destinationKey :: keys.length :: keys.value :: HNil).as[Integer, NonNegInt]

  final def zunionstoreweighted(weightedKeys: TwoOrMoreWeightedKeys, destinationKey: Key): Protocol.Aux[NonNegInt] = {
    val (keys, weights) = weightedKeys.unzip
    Protocol("ZUNIONSTORE", destinationKey :: keys.length :: keys :: "WEIGHTS" :: weights :: HNil)
      .as[Integer, NonNegInt]
  }

  final def zunionstore(keys: TwoOrMoreKeys, destinationKey: Key, aggregate: Aggregate): Protocol.Aux[NonNegInt] =
    Protocol("ZUNIONSTORE", destinationKey :: keys.length :: keys.value :: "AGGREGATE" :: aggregate :: HNil)
      .as[Integer, NonNegInt]

  final def zunionstoreweighted(
      weightedKeys: TwoOrMoreWeightedKeys,
      destinationKey: Key,
      aggregate: Aggregate
  ): Protocol.Aux[NonNegInt] = {
    val (keys, weights) = weightedKeys.unzip
    Protocol(
      "ZUNIONSTORE",
      destinationKey :: keys.length :: keys :: "WEIGHTS" :: weights :: "AGGREGATE" :: aggregate :: HNil
    ).as[Integer, NonNegInt]
  }
}

trait AllSortedSetP extends SortedSetP with SortedSetPExtra
