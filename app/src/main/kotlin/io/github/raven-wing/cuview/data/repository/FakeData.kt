package io.github.raven_wing.cuview.data.repository

import io.github.raven_wing.cuview.data.model.CUList
import io.github.raven_wing.cuview.data.model.CUView
import io.github.raven_wing.cuview.data.model.CUFolder
import io.github.raven_wing.cuview.data.model.CUSpace
import io.github.raven_wing.cuview.data.model.CUTask
import io.github.raven_wing.cuview.data.model.TaskListRef

internal object FakeData {

    // ── Spaces ────────────────────────────────────────────────────────────────

    val spaces = listOf(
        CUSpace("mock_space_mammals", "Mammals"),
        CUSpace("mock_space_birds", "Birds"),
    )

    // ── Space contents ────────────────────────────────────────────────────────

    val spaceContents: Map<String, SpaceContents> = mapOf(
        "mock_space_mammals" to SpaceContents(
            spaceViews = emptyList(),
            folders = listOf(
                CUFolder("mock_folder_hoofed", "Hoofed Animals", listOf(
                    CUList("mock_list_horse",  "Horse"),
                    CUList("mock_list_zebra",  "Zebra"),
                    CUList("mock_list_donkey", "Donkey"),
                )),
                CUFolder("mock_folder_felines", "Felines", listOf(
                    CUList("mock_list_lion",    "Lion"),
                    CUList("mock_list_cheetah", "Cheetah"),
                    CUList("mock_list_tiger",   "Tiger"),
                )),
            ),
            folderlessLists = listOf(
                CUList("mock_list_bat",     "Bat"),
                CUList("mock_list_dolphin", "Dolphin"),
            ),
        ),
        "mock_space_birds" to SpaceContents(
            spaceViews = emptyList(),
            folders = listOf(
                CUFolder("mock_folder_raptors", "Raptors", listOf(
                    CUList("mock_list_eagle", "Eagle"),
                    CUList("mock_list_hawk",  "Hawk"),
                    CUList("mock_list_owl",   "Owl"),
                )),
            ),
            folderlessLists = listOf(
                CUList("mock_list_flamingo", "Flamingo"),
                CUList("mock_list_penguin",  "Penguin"),
            ),
        ),
    )

    // ── Views ─────────────────────────────────────────────────────────────────

    // Folder-level views — none in mock (folders are just containers).
    val folderViews: Map<String, List<CUView>> = emptyMap()

    // Each list gets one "List" view; ID pattern: mock_view_<animal>.
    val listViews: Map<String, List<CUView>> = listOf(
        "horse", "zebra", "donkey",
        "lion", "cheetah", "tiger",
        "bat", "dolphin",
        "eagle", "hawk", "owl",
        "flamingo", "penguin",
    ).associate { animal ->
        "mock_list_$animal" to listOf(CUView("mock_view_$animal", "List", "list"))
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    private fun tasks(listId: String, vararg names: String): List<CUTask> =
        names.mapIndexed { i, name -> CUTask("${listId}_t$i", name, TaskListRef(listId)) }

    private val tasksByList: Map<String, List<CUTask>> = mapOf(
        "mock_list_horse"    to tasks("mock_list_horse",    "Brush mane", "Clean hooves", "Schedule vet visit", "Replenish hay supply"),
        "mock_list_zebra"    to tasks("mock_list_zebra",    "Herd check", "Waterhole patrol", "Stripe grooming"),
        "mock_list_donkey"   to tasks("mock_list_donkey",   "Saddle maintenance", "Trail walk", "Grooming session"),
        "mock_list_lion"     to tasks("mock_list_lion",     "Pride territory patrol", "Hunt coordination", "Cub training session", "Mane grooming"),
        "mock_list_cheetah"  to tasks("mock_list_cheetah",  "Sprint training", "Cub agility drills", "Territory marking"),
        "mock_list_tiger"    to tasks("mock_list_tiger",    "Territory marking", "Swimming practice", "Stripe inspection"),
        "mock_list_bat"      to tasks("mock_list_bat",      "Echolocation calibration", "Cave inspection", "Night patrol"),
        "mock_list_dolphin"  to tasks("mock_list_dolphin",  "Pod synchronization", "Trick rehearsal", "Echolocation training"),
        "mock_list_eagle"    to tasks("mock_list_eagle",    "Nest maintenance", "Territory scan", "Fledgling training"),
        "mock_list_hawk"     to tasks("mock_list_hawk",     "Dive practice", "Perch inspection", "Prey tracking"),
        "mock_list_owl"      to tasks("mock_list_owl",      "Nocturnal patrol", "Silent flight drill", "Pellet analysis"),
        "mock_list_flamingo" to tasks("mock_list_flamingo", "Flock synchronization", "Nesting preparation", "Filter feeding session"),
        "mock_list_penguin"  to tasks("mock_list_penguin",  "Waddle coordination", "Diving practice", "Egg incubation schedule"),
    )

    fun tasksForList(listId: String): List<CUTask> =
        tasksByList[listId] ?: emptyList()

    fun tasksForView(viewId: String): List<CUTask> =
        tasksByList["mock_list_" + viewId.removePrefix("mock_view_")] ?: emptyList()
}
