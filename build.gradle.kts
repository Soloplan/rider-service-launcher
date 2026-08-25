//-----------------------------------------------------------------------
// <copyright file="build.gradle.kts" company="Soloplan GmbH">
// Copyright (c) Soloplan GmbH. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for license information.
// </copyright>
//-----------------------------------------------------------------------

plugins {
  java
  id("org.jetbrains.intellij.platform") version "2.9.0"
}

group = "local.soloplan.tools"
version = "0.6.0"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    local("C:/Program Files/JetBrains/JetBrains Rider 2025.2.2.1")
  }
  testImplementation("junit:junit:4.13.2")
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.add("-Xlint:all,-serial")
}

intellijPlatform {
  buildSearchableOptions = false

  pluginConfiguration {
    ideaVersion {
      sinceBuild = "252"
      untilBuild = provider { null }
    }
  }
}
