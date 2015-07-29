(ns leiningen.rpm
  (:require [clojure.java.data :as data])
  (:import [org.codehaus.mojo.rpm RPMMojo AbstractRPMMojo Mapping Source SoftlinkSource Scriptlet Passphrase]
           [org.apache.maven.execution MavenSession]
           [org.apache.maven.settings Settings]
           [org.apache.maven.project MavenProject]
           [org.apache.maven.shared.filtering DefaultMavenFileFilter]
           [org.codehaus.plexus.logging.console ConsoleLogger]))

(defn create-array-list [clj-seq]
  (let [al (java.util.ArrayList.)]
    (doseq [item clj-seq] (.add al item))
    al))

(defn create-source [class [source & rest]]
  (if source (cons (data/to-java class source) (create-source class rest)) ()))

(defn create-sources [{:keys [source softlinkSource]}]
  (println "source->" source)
  (concat (create-source Source source) (create-source SoftlinkSource softlinkSource)))

(defn create-mapping [{s :sources :as mapping}]
  (data/to-java Mapping (assoc mapping :sources (create-sources s))))

(defn create-mappings [[mapping & rest]]
  (if mapping (cons (create-mapping mapping) (create-mappings rest)) ()))

(defn set-mojo! [object name value]
  (let [field (.getDeclaredField AbstractRPMMojo name)]
    (.setAccessible field true)
    (.set field object value))
  object)

(defn createBaseMojo []
  (let [mojo (RPMMojo.)
        fileFilter (DefaultMavenFileFilter.)]
    (set-mojo! mojo "project" (MavenProject.))
    (.enableLogging fileFilter (ConsoleLogger. 0 "Logger"))
    (set-mojo! mojo "mavenFileFilter" fileFilter)))

(defn if-key-update [m k f]
  (if-let [v (get m k)] (assoc m k (f v)) m))

(defn create-scriptlet [s]
  (data/to-java Scriptlet (if-key-update s :scriptFile #(clojure.java.io/file %))))

(defn create-dependency [rs]
  (let [hs (java.util.LinkedHashSet.)]
    (doseq [r rs] (.add hs r))
    hs))

(defn rpm
  "Create an RPM"
  [{{:keys [summary name license mappings define-statements prefix preinstall install postinstall preremove postremove 
            requires provides conflicts workarea]} :rpm :keys [version]} & keys]
  (let [mojo (createBaseMojo)]
    (set-mojo! mojo "projversion" version)
    (set-mojo! mojo "versionProperty" version)
    (set-mojo! mojo "releaseProperty" version)
    (set-mojo! mojo "session" (MavenSession. nil (Settings.) nil nil nil nil
                                             nil (java.util.Properties.) (java.util.Date.)))
    (set-mojo! mojo "keyPassphrase" (doto (Passphrase.) (.setPassphrase "foo")))
    (set-mojo! mojo "name" name)
    (set-mojo! mojo "summary" summary)
    (set-mojo! mojo "license" license)
    (set-mojo! mojo "workarea" (clojure.java.io/file workarea)) 
    (set-mojo! mojo "mappings" (create-array-list (create-mappings mappings)))
    (set-mojo! mojo "defineStatements" (create-array-list define-statements))
    (set-mojo! mojo "prefix" prefix)
    (set-mojo! mojo "preinstallScriptlet" (let [s (create-scriptlet preinstall)] (do (println (.getScriptFile s)) s)))
    (set-mojo! mojo "installScriptlet" (create-scriptlet install))
    (set-mojo! mojo "postinstallScriptlet" (create-scriptlet postinstall))
    (set-mojo! mojo "preremoveScriptlet" (create-scriptlet preremove))
    (set-mojo! mojo "postremoveScriptlet" (create-scriptlet postremove))
    
    (set-mojo! mojo "requires" (create-dependency requires))
    (set-mojo! mojo "provides" (create-dependency provides))
    (set-mojo! mojo "conflicts" (create-dependency conflicts))
    (.execute mojo)))
