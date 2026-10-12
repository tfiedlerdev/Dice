package tfdev.dice

/** One die's result in a settled roll - null [faceValue] means it didn't come to rest level on a single face. */
data class DieResult(val faceValue: Int?, val colorArgb: Int)

/** One "all dice came to a stop" event - [dieResults] is a snapshot, so later recoloring a die doesn't rewrite history. */
data class RollEntry(val timestampMillis: Long, val dieResults: List<DieResult>)
