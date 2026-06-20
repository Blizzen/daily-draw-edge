// Root build — plugins are applied in the module builds; declared here (apply
// false) so versions resolve consistently across :core and :app.
plugins {
    kotlin("jvm") version "2.0.20" apply false
    kotlin("android") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
    kotlin("plugin.compose") version "2.0.20" apply false
    id("com.android.application") version "8.6.0" apply false
}
