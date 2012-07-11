package org.dbpedia.extraction.dump.download

import scala.collection.mutable.{Set,Map,HashMap}
import scala.collection.immutable.SortedSet
import java.net.{URL,URLConnection}
import java.io.{File,InputStream,IOException}
import scala.io.{Source,Codec}
import org.dbpedia.extraction.util.Finder
import org.dbpedia.extraction.util.RichFile.toRichFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.util.zip.GZIPInputStream

/**
 */
class DumpDownload(baseUrl: URL, baseDir: File, dateRange: (String, String), dumpCount: Int, downloader: Downloader)
{
  /**
   * @param files language code -> file names
   */
  def downloadFiles(files: Map[String, Set[String]]): Unit =
  {
    // sort them to have reproducible behavior
    val keys = SortedSet.empty[String] ++ files.keys
    keys.foreach { key => 
      val done = keys.until(key)
      val todo = keys.from(key)
      println("done: "+done.size+" - "+done.mkString(","))
      println("todo: "+todo.size+" - "+keys.from(key).mkString(","))
      new LanguageDownload(key, files(key)).downloadDates(dateRange)
    }
  }
  
  val DateLink = """<a href="(\d{8})/">""".r
  
  class LanguageDownload(language: String, fileNames: Set[String]) {
    
    val finder = new Finder[File](baseDir, language)
    
    val wiki = finder.wikiName
    
    val mainPage = new URL(baseUrl, wiki+"/") // here the server does NOT use index.html 
    val mainDir = new File(baseDir, wiki)
    if (! mainDir.exists && ! mainDir.mkdirs) throw new Exception("Target directory ["+mainDir+"] does not exist and cannot be created")
    
    def downloadDates(dateRange: (String, String)): Unit = {
      
      val firstDate = dateRange._1
      val lastDate = dateRange._2
      
      val started = finder.file(Download.Started)
      if (! started.createNewFile) throw new Exception("Another process may be downloading files to ["+mainDir+"] - stop that process and remove ["+started+"]")
      try {
        
        // find all dates on the main page, sort them latest first
        var dates = SortedSet.empty(Ordering[String].reverse)
        
        downloader.downloadTo(mainPage, mainDir) // creates index.html, although it does not exist on the server
        forEachLine(new File(mainDir, "index.html")) { line => 
          DateLink.findAllIn(line).matchData.foreach(dates += _.group(1))
        }
        
        var count = 0
      
        // find date pages that have all files we want
        for (date <- dates) {
          if (count < dumpCount && date >= firstDate && date <= lastDate && downloadDate(date)) count += 1 
        }
      
        if (count == 0) throw new Exception("found no date on "+mainPage+" in range "+firstDate+"-"+lastDate+" with files "+fileNames.mkString(","))
      }
      finally started.delete
    }
    
    def downloadDate(date: String): Boolean = {
      
      val datePage = new URL(mainPage, date+"/") // here we could use index.html
      val dateDir = new File(mainDir, date)
      if (! dateDir.exists && ! dateDir.mkdirs) throw new Exception("Target directory '"+dateDir+"' does not exist and cannot be created")
      
      val complete = finder.file(date, Download.Complete)
      
      val urls = fileNames.map(fileName => new URL(baseUrl, wiki+"/"+date+"/"+wiki+"-"+date+"-"+fileName))
      
      if (complete.exists) {
        // Previous download process said that this dir is complete. Note that we MUST check the
        // 'complete' file - the previous download may have crashed before all files were fully
        // downloaded. Checking that the downloaded files exist is necessary but not sufficient.
        // Checking the timestamps is sufficient but not efficient.
        
        if (urls.forall(url => new File(dateDir, downloader.targetName(url)).exists)) {
          println("did not download any files to '"+dateDir+"' - all files already complete")
          return true
        } 
        
        // Some files are missing. Maybe previous process was configured for different files.
        // Download the files that are missing or have the wrong timestamp. Delete 'complete' 
        // file first in case this download crashes. 
        complete.delete
      }
      
      // all the links we need
      val links = new HashMap[String, String]()
      for (fileName <- fileNames) links(fileName) = "<a href=\"/"+wiki+"/"+date+"/"+wiki+"-"+date+"-"+fileName+"\">"
      
      downloader.downloadTo(datePage, dateDir) // creates index.html
      forEachLine(new File(dateDir, "index.html")) { line => 
        links.foreach{ case (fileName, link) => if (line contains link) links -= fileName }
      }
      
      // did we find them all?
      if (links.nonEmpty) {
        println("date page '"+datePage+"' has no links to ["+links.keys.mkString(",")+"]")
        false
      }
      else {
        println("date page '"+datePage+"' has all files ["+fileNames.mkString(",")+"]")
        // download all files
        for (url <- urls) downloader.downloadTo(url, dateDir)
        complete.createNewFile
        true
      }
    }
    
  }
  
  private def forEachLine(file: File)(process: String => Unit): Unit = {
    val source = Source.fromFile(file)(Codec.UTF8)
    try for (line <- source.getLines) process(line)
    finally source.close
  }
  
}
