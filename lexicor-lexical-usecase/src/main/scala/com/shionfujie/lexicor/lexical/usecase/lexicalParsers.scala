package com.shionfujie.lexicor.lexical.usecase

import com.shionfujie.lexicor.core.domain._
import com.shionfujie.lexicor.core.domain.Lexemes.{Keyword, Subject}

import scala.util.matching.Regex
import scala.util.parsing.combinator.Parsers

/** Provides factory methods construct a parser that understands the language with the power of [[Parsers]]. */
private object lexicalParsers extends Parsers {

  type Elem = Char

  private val whiteSpace = """\s+""".r

  /** Behaves as the same as [[Parsers.accept]] except an [[Elem]] with a [[Pos]] being returned instead. */
  implicit def chr(e: Elem): Parser[Elem With Pos] =
    acceptIf2(_ == e)("'" + e + "' expected but " + _ + " found")

  /** A parser that matches a string and returns it to a [[String]] with [[Pos]]. */
  implicit def literal(s: String): Parser[String With Pos] = in => {
    val source = in.source
    val offset = in.offset
    val start = handleWhiteSpace(source, offset)
    var i = 0
    var j = start
    while (i < s.length && j < source.length && s.charAt(i) == source.charAt(j)) {
      i += 1
      j += 1
    }
    if (i == s.length)
      Success(((start, j - 1), source.subSequence(start, j).toString), in.drop(j - offset))
    else {
      val found =
        if (start == source.length()) "end of source"
        else "'" + source.charAt(start) + "'"
      Failure(
        "'" + s + "' expected but " + found + " found",
        in.drop(start - offset)
      )
    }
  }

  /** A parser that matches a regex string and returns it to a [[String]] with a [[Pos]] */
  implicit def regex(r: Regex): Parser[String With Pos] = in => {
    val source = in.source
    val offset = in.offset
    val start = handleWhiteSpace(source, offset)
    r.findPrefixMatchOf(new SubSequence(source, start)) match {
      case Some(matched) =>
        val end = start + matched.end
        Success(
          ((start, end - 1), source.subSequence(start, end).toString),
          in.drop(end - offset)
        )
      case None =>
        val found =
          if (start == source.length()) "end of source"
          else "'" + source.charAt(start) + "'"
        Failure(
          "string matching regex '" + r + "' expected but " + found + " found",
          in.drop(start - offset)
        )
    }
  }

  /** A parser that matches a symbol's name and returns it to a [[Keyword]] */
  def keyword(symbol: Symbol): Parser[Lexeme] =
    for ((pos, _) <- literal(symbol.name)) yield Keyword(pos, symbol)

  /** A parser that matches a symbol's name and returns it to a [[Subject]] */
  def subject(symbol: Symbol): Parser[Lexeme] =
    for ((pos, _) <- literal(symbol.name)) yield Subject(pos, symbol)

  /** A parser that matches what `p` matches which is parenthesised, i.e., following `opening` and
    * preceding `closing`, and returns the result of `p` with a [[Pos]]. */
  def parens[T](opening: Elem, p: => Parser[T], closing: Elem): Parser[T With Pos] =
    opening ~ p ~ closing ^^ { case (start, _) ~ t ~ ((end, _)) => (start bridgeTo end, t) }

  private[lexical] def unzip[T](p: => Parser[T With Pos]): Parser[T] = for ((_, t) <- p) yield t

  /** Behaves as the same as [[Parsers.acceptIf]] except an [[Elem]] with a [[Pos]] being returned instead. */
  private def acceptIf2(p: Elem => Boolean)(err: Elem => String): Parser[Elem With Pos] =
    in =>
      if (in.atEnd) Failure("end of input", in)
      else if (p(in.first)) Success(((in.offset, in.offset), in.first), in.rest)
      else Failure(err(in.first), in)

  /** Behaves as the same as [[scala.util.parsing.combinator.RegexParsers.handleWhiteSpace]] except that it always skips white spaces */
  private def handleWhiteSpace(source: CharSequence, offset: Int): Int =
    whiteSpace.findPrefixMatchOf(new SubSequence(source, offset)) match {
      case Some(matched) => offset + matched.end
      case None          => offset
    }

}
