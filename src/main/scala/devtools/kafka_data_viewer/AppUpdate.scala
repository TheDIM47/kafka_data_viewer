package devtools.kafka_data_viewer

import java.net.{HttpURLConnection, URL}
import java.util.Properties

import com.fasterxml.jackson.databind.ObjectMapper

import scala.util.Try

object AppUpdate {

  case class AppUpdateInfo(currentVersion: String,
                           newVersion: String,
                           updateUrl: String)

  def verify(): Option[AppUpdateInfo] = {
    Try(for {
      propsStream <- Option(
        AppUpdate.getClass.getClassLoader
          .getResourceAsStream("build.properties")
      )
      props <- Try { val p = new Properties(); p.load(propsStream); p }.toOption
      version = props.getProperty("version")
      url = new URL(props.getProperty("url"))
      updateUrl = props.getProperty("update_url")
      info <- ResourceUtility.using(url.openConnection())(_ => ()) {
        con =>
          val newVersion =
            ResourceUtility.using(con.getInputStream)(_.close()) { in =>
              val mapper = new ObjectMapper()
              val json = mapper.readValue(in, classOf[java.util.HashMap[_, _]])
              json.get("name").asInstanceOf[String]
            }
          if (version != newVersion)
            Some(
              AppUpdateInfo(
                currentVersion = version,
                newVersion = newVersion,
                updateUrl = updateUrl
              )
            )
          else
            None
      }
    } yield info)
      .recover {
        case e =>
          e.printStackTrace()
          None
      }
      .toOption
      .flatten
  }
}
