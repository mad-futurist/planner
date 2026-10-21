package app.planner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.planner.core.designsystem.*
import app.planner.core.model.Record
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

/** Fixed records/dates, cropped to the component surface; no production database or clock. */
class DesignSystemScreenshotTest {
    @get:Rule val ui = createComposeRule()
    @Test fun lightComponentsMatchReviewedGolden() = verify("light")
    @Test fun darkComponentsMatchReviewedGolden() = verify("dark")

    private fun verify(appearance: String) {
        val record=Record(id="golden-task",sourceNoteId="fixture",title="Finish draft",plannedFor="2026-10-21",pinned=true,createdAt="2026-10-21T08:00:00.000Z")
        ui.setContent {
            PlannerTheme(appearance) {
                Column(Modifier.width(360.dp).background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("golden-surface")) {
                    Text("Today",style=MaterialTheme.typography.displayLarge,color=MaterialTheme.colorScheme.onSurface)
                    SectionHeader("Schedule",Glyph.Calendar)
                    ScheduleCard(record.copy(baseKind="event",title="Meeting",startAt="2026-10-21T13:00:00.000Z",timezone="UTC"),LocalDate.of(2026,10,21)) {}
                    SectionHeader("Anytime",Glyph.Sun,warm=true)
                    TaskCard(record,{}, {},compact=true)
                    SectionHeader("Idea of the day",Glyph.Bulb,violet=true)
                    IdeaCard(record.copy(baseKind="idea",title="Volunteer program",fieldsJson="""{"details":"Build a community tutoring program."}"""),{}, {},later={})
                }
            }
        }
        ui.waitForIdle()
        val actual=ui.onNodeWithTag("golden-surface").captureToImage().asAndroidBitmap()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val name="design-system-$appearance.png"
        val output=File(instrumentation.targetContext.getExternalFilesDir(null),"goldens/$name").apply {parentFile!!.mkdirs()}
        output.outputStream().use {actual.compress(Bitmap.CompressFormat.PNG,100,it)}
        if(InstrumentationRegistry.getArguments().getString("recordGoldens")=="true") return
        val expected=instrumentation.context.assets.open("goldens/$name").use {BitmapFactory.decodeStream(it)}
        assertEquals("Golden width changed",expected.width,actual.width)
        assertEquals("Golden height changed",expected.height,actual.height)
        val before=IntArray(actual.width*actual.height);val after=IntArray(before.size)
        expected.getPixels(before,0,expected.width,0,0,expected.width,expected.height)
        actual.getPixels(after,0,actual.width,0,0,actual.width,actual.height)
        val changed=before.indices.count {i-> listOf(0,8,16).any {shift->abs(((before[i] shr shift) and 255)-((after[i] shr shift) and 255))>16}}
        val fraction=changed.toDouble()/before.size
        assertTrue("$name differs at ${"%.2f".format(fraction*100)}% of pixels; review actual output before replacing the baseline",fraction<=.005)
    }
}
