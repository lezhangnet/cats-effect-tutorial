/*
 * Copyright (c) 2018 Luis Rodero-Merino
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package my

import java.io._

import cats.effect._
import cats.effect.{Concurrent, IO, Resource}
import cats.effect.concurrent.Semaphore
import cats.effect.internals.IOAppPlatform
import cats.implicits._

object CopyFile extends App {

  // copy from IOApp - IOAppPlatform not accessible !
  // protected implicit def contextShift: ContextShift[IO] = IOAppPlatform.defaultContextShift

  def transmit(origin: InputStream, destination: OutputStream, buffer: Array[Byte], acc: Long): IO[Long] =
    for {
      amount <- IO(origin.read(buffer, 0, buffer.length))
      count  <- if(amount > -1) IO(destination.write(buffer, 0, amount)) >> transmit(origin, destination, buffer, acc + amount)
                else IO.pure(acc) // End of read stream reached (by java.io.InputStream contract), nothing to write
    } yield count // Returns the actual amount of bytes transmitted

  def transfer(origin: InputStream, destination: OutputStream): IO[Long] =
    for {
      buffer <- IO{ new Array[Byte](1024 * 10) } // Allocated only when the IO is evaluated
      total  <- transmit(origin, destination, buffer, 0L)
    } yield total

  def inputStream(f: File, guard: Semaphore[IO]): Resource[IO, FileInputStream] =
    Resource.make {
      IO(new FileInputStream(f))
    } { inStream => 
      guard.withPermit {
       IO(inStream.close()).handleErrorWith(_ => IO.unit)
      }
    }

  def outputStream(f: File, guard: Semaphore[IO]): Resource[IO, FileOutputStream] =
    Resource.make {
      IO(new FileOutputStream(f))
    } { outStream =>
      guard.withPermit {
       IO(outStream.close()).handleErrorWith(_ => IO.unit)
      }
    }

  def inputOutputStreams(in: File, out: File, guard: Semaphore[IO]): Resource[IO, (InputStream, OutputStream)] =
    for {
      inStream  <- inputStream(in, guard)
      outStream <- outputStream(out, guard)
    } yield (inStream, outStream)

  def copy(origin: File, destination: File): IO[Long] = ???
  /*
    for {
      guard <- Semaphore[IO](1) // missing implicit Concurrent[IO]
      count <- inputOutputStreams(origin, destination, guard).use { case (in, out) => 
                 guard.withPermit(transfer(in, out))
               }
    } yield count

   */

  // The 'main' function of IOApp //
  //override def run(args: List[String]): IO[ExitCode] = {
    println("zhale:CopyFile")
    for {
      _ <- if (args.length < 2) IO.raiseError(new IllegalArgumentException("Need origin and destination files"))
           else IO.unit
      orig = new File(args.head)
      dest = new File(args.tail.head)
      count <- copy(orig, dest)
      _ <- IO(println(s"$count bytes copied from ${orig.getPath} to ${dest.getPath}"))
    } yield ExitCode.Success
  //}
}
