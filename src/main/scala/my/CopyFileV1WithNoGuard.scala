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

import java.io.{File, FileInputStream, FileOutputStream, InputStream, OutputStream}
import cats.effect._
import cats.implicits._

// same as CopyFile, but as V1 with NO Semaphore / guard - ref doc

object CopyFileV1WithNoGuard extends App {

  // loop
  def transmit(origin: InputStream, destination: OutputStream, buffer: Array[Byte], acc: Long): IO[Long] = {
    println("zhale:transmit()")
    for {
      // read
      amount <- IO(origin.read(buffer, 0, buffer.length))
      // write
      count  <- if(amount > -1)
                  IO(destination.write(buffer, 0, amount)) >> transmit(origin, destination, buffer, acc + amount) // looping
                else
                  IO.pure(acc) // End of read stream reached (by java.io.InputStream contract), nothing to write
    } yield count // Returns the actual amount of bytes transmitted
  }

  def transfer(origin: InputStream, destination: OutputStream): IO[Long] = {
    println("zhale:transfer()")
    for {
      buffer <- IO{ new Array[Byte](1024 * 10) } // Allocated only when the IO is evaluated
      total  <- transmit(origin, destination, buffer, 0L)
    } yield total
  }

  def inputStream(f: File): Resource[IO, FileInputStream] = {
    println("zhale:inputStream():file:" + f)
    Resource.make {
      IO(new FileInputStream(f)) // build
    } { inStream =>
      IO(inStream.close()).handleErrorWith(ex => {
        println("zhale:inputStream()ex:" + ex)
        IO.unit
      }) // release; just swallowing any error
    }
  }

  def outputStream(f: File): Resource[IO, FileOutputStream] = {
    println("zhale:outputStream()")
    Resource.make {
      IO(new FileOutputStream(f)) // build
    } { outStream =>
      IO(outStream.close()).handleErrorWith(ex => {
        println("zhale:outputStream()ex:" + ex)
        IO.unit
      }) // release
    }
  }

  def inputOutputStreams(in: File, out: File): Resource[IO, (InputStream, OutputStream)] = {
    println("zhale:inputOutputStreams()")
    for {
      inStream  <- inputStream(in)
      outStream <- outputStream(out)
    } yield (inStream, outStream)
  }

  // auto version lacks control on releasing exceptions
  def inputStreamAuto(f: File): Resource[IO, FileInputStream] = {
    println("zhale:inputAuto()")
    Resource.fromAutoCloseable(IO(new FileInputStream(f)))
  }

  def outputStreamAuto(f: File): Resource[IO, FileOutputStream] = {
    println("zhale:outputAuto()")
    Resource.fromAutoCloseable(IO(new FileOutputStream(f)))
  }

  def inputOutputStreamsAuto(in: File, out: File): Resource[IO, (InputStream, OutputStream)] = {
    println("zhale:Auto()")
    for {
      inStream  <- inputStreamAuto(in)
      outStream <- outputStreamAuto(out)
    } yield (inStream, outStream)
  }

  def copy(origin: File, destination: File): IO[Long] = {
    println("zhale:copy()")
    //inputOutputStreams(origin, destination).use { case (in, out) =>
    inputOutputStreamsAuto(origin, destination).use { case (in, out) =>
      // this will not run if either stream resources are not obtained
      transfer(in, out)
    } // stream resources will be auto-closed after "use"
  }

  // The 'main' function of IOApp //
  // override def run(args: List[String]): IO[ExitCode] =
  println("zhale:CopyFileWithNoGuard:args:" + args.toList) // List()
  val orig = new File("/Users/zhale/zCode/cats-effect-tutorial/src/main/scala/my/CopyFile.scala")
  val dest = new File("/Users/zhale/zCode/cats-effect-tutorial/src/main/scala/my/CopyFileDestination")
  val count = copy(orig, dest)
  println(count) // IO$xxx
  println(count.unsafeRunSync()) // 3046; the IO will not execute until / without this
  for {
    _ <- IO(println(s"$count bytes copied from ${orig.getPath} to ${dest.getPath}")) // this will NOT print
  } yield ExitCode.Success
}
