package tv.still.fixture

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Shows its own name; Back returns to the launcher. */
class FixtureActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(TextView(this).apply { text = getString(R.string.fixture_label); textSize = 32f; setPadding(96, 96, 96, 96) })
    }
}
