(defproject com.rpl/building-with-rama-clj "1.0.0-SNAPSHOT"
  :dependencies [[com.rpl/rama-helpers "0.10.0"]
                 [org.apache.logging.log4j/log4j-slf4j18-impl "2.16.0"]
                 [org.asynchttpclient/async-http-client "2.12.3"]]
  :repositories [["releases" {:id "maven-releases"
                              :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]

  :profiles {:dev {:resource-paths ["test/resources/"]}
             :provided {:dependencies [[com.rpl/rama "1.0.0"]]}}
  )
