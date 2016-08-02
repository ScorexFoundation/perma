package scorex.perma.storage

import java.io.File

import org.scalacheck.Arbitrary
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.Generators
import scorex.crypto.authds.merkle.MerklePath
import scorex.perma.consensus.PermaAuthData


class AuthDataStorageSpecification extends PropSpec with PropertyChecks with GeneratorDrivenPropertyChecks with Matchers
with Generators {

  val treeDirName = "/tmp/scorex-test/test/AuthDataStorageSpecification/"
  val treeDir = new File(treeDirName)
  treeDir.mkdirs()

  val keyVal = for {
    key: Long <- Arbitrary.arbitrary[Long]
    b <- sizedBytes
    value <- Arbitrary.arbitrary[String]
  } yield (key, new PermaAuthData(value.getBytes, MerklePath(0, Seq(b.unsized))))


  property("set value and get it") {
    lazy val storage = new AuthDataStorage(None)

    forAll(keyVal) { x =>
      val key = x._1
      val value = x._2
      storage.set(key, value)

      assert(storage.get(key).get.data sameElements value.data)
    }
    storage.close()
  }
}