/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.parser.v1_6

import org.neo4j.cypher.commands._
import org.neo4j.cypher.SyntaxException

class CypherParser extends Base
with StartClause
with MatchClause
with WhereClause
with ReturnClause
with SkipLimitClause
with OrderByClause {

  def query: Parser[String => Query] = start ~ opt(matching) ~ opt(where) ~ returns ~ opt(order) ~ opt(skip) ~ opt(limit) ^^ {

    case start ~ matching ~ where ~ returns ~ order ~ skip ~ limit => {
      val slice = (skip, limit) match {
        case (None, None) => None
        case (s, l) => Some(Slice(s, l))
      }

      val (pattern: Option[Match], namedPaths: Option[NamedPaths]) = matching match {
        case Some((p, NamedPaths())) => (Some(p), None)
        case Some((Match(), nP)) => (None, Some(nP))
        case Some((p, nP)) => (Some(p), Some(nP))
        case None => (None, None)
      }

      (queryText: String) => Query(returns._1, start, pattern, where, returns._2, order, slice, namedPaths, queryText)
    }
  }

  private def findErrorLine(idx: Int, message: Seq[String]): String =
    message.toList match {
      case Nil => "oops"

      case List(x) => {
        val i = if (x.size > idx)
          idx
        else
          x.size

        "\"" + x + "\"\n" + " " * i + " ^"
      }

      case head :: tail => if (head.size > idx) {
        "\"" + head + "\"\n" + " " * idx + " ^"
      } else {
        findErrorLine(idx - head.size - 1, tail) //The extra minus one is there for the now missing \n
      }
    }


  def fail(input: Input, errorMessage: String): Nothing = {
    val location = findErrorLine(input.offset, input.source.toString.split("\n"))

    throw new SyntaxException(errorMessage + "\n" + location)
  }

  @throws(classOf[SyntaxException])
  def parse(queryText: String): Query = {
    val MissingQuoteError = """`\.' expected but `.' found""".r
    val MissingStartError = """string matching regex `\(\?i\)\\Qstart\\E' expected.*""".r
    val WholeNumberExpected = """string matching regex `\\d\+' expected.*""".r
    val StringExpected = """string matching regex `'\(\[\^'\\p\{Cntrl\}\\\\\]\|\\\\\[\\\\\/bfnrt\]\|\\\\u\[a-fA-F0-9\]\{4\}\)\*'' .*""".r

    parseAll(query, queryText) match {
      case Success(r, q) => r(queryText)
      case NoSuccess(message, input) => fail(input, message)

      /*message match {
        case MissingQuoteError() => fail(input, "Probably missing quotes around a string")
        case MissingStartError() => fail(input, "Missing START clause")
        case WholeNumberExpected() => fail(input, "Whole number expected")
        case StringExpected() => fail(input, "String literal expected")
        case "string matching regex `-?\\d+' expected but `)' found" => fail(input, "Last element of list must be a value")
        case "string matching regex `(?i)\\Qreturn\\E' expected but end of source found" => throw new SyntaxException("Missing RETURN clause")
        case _ =>
      }*/
    }
  }

}