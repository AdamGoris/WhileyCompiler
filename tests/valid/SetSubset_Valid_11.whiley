import whiley.lang.*

function f({int} xs, {int} ys) -> bool
requires xs ⊆ ys:
    return true

function g({int} xs, {int} ys) -> bool
requires xs ⊆ ys:
    return f(xs, ys)

method main(System.Console sys) -> void:
    assume g({1, 2, 3}, {1, 2, 3})
    assume g({1, 2}, {1, 2, 3})
    assume g({1}, {1, 2, 3})
