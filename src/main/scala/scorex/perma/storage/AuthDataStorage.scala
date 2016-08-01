package scorex.perma.storage

import org.h2.mvstore.{MVMap, MVStore}
import scorex.crypto.authds.storage.{KVStorage, MvStoreStorageType}
import scorex.perma.consensus.PermaAuthData
import scorex.perma.settings.PermaConstants.DataSegmentIndex

class AuthDataStorage(fileOpt: Option[String]) extends KVStorage[DataSegmentIndex, PermaAuthData, MvStoreStorageType] {

  val db = fileOpt match {
    case Some(file) => new MVStore.Builder().fileName(file).compress().open()
    case None => new MVStore.Builder().open()
  }
  val segments: MVMap[DataSegmentIndex, Array[Byte]] = db.openMap("segments")

  override def set(key: DataSegmentIndex, value: PermaAuthData): Unit = segments.put(key, value.bytes)

  override def get(key: DataSegmentIndex): Option[PermaAuthData] = Option(segments.get(key))
    .flatMap(b => PermaAuthData.parseBytes(b).toOption)

  override def size: DataSegmentIndex = segments.sizeAsLong()

  override def unset(key: DataSegmentIndex): Unit = segments.remove(key)

  override def close(): Unit = db.close()

  override def commit(): Unit = db.commit()
}
