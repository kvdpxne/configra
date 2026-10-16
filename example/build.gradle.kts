plugins {
  application
}

application {
  mainClass = "me.kvd.properties.example.ExampleMain"
  applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

dependencies {
  arrayOf("api", "core", "schema", "spi", "formats:ini", "formats:properties").forEach {
    implementation(project(":$it"))
  }
}

tasks.named("publish") {
  enabled = false
}