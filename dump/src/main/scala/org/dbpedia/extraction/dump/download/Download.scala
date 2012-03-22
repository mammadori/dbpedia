package org.dbpedia.extraction.dump.download

import scala.collection.mutable.{Set,HashSet,HashMap}
import java.net.{URL,MalformedURLException}
import java.io.{File,InputStream}
import scala.io.{Source,Codec}
import java.util.TreeSet
import java.util.Collections.reverseOrder
import scala.collection.JavaConversions.asScalaSet

object Download
{
  def main(args: Array[String]) : Unit =
  {
    val cfg = new Config
    cfg.parse(args)
    cfg.validate
    
    val downloader = new Downloader(cfg.baseUrl, cfg.baseDir, cfg.retryMax, cfg.retryMillis, cfg.unzip)
    
    downloader.init
    
    // download other files, may be none
    if (cfg.others.nonEmpty)
    {
      downloader.download(cfg.others)
    }
    
    // resolve page count ranges to languages
    if (cfg.ranges.nonEmpty)
    {
      downloader.resolveRanges(cfg.csvUrl, cfg.ranges, cfg.languages)
    }
    
    // download the dump files, if any
    if (cfg.languages.nonEmpty)
    {
      downloader.downloadFiles(cfg.languages)
    }
  }
}


