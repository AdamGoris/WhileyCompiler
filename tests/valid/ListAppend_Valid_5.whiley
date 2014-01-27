import println from whiley.lang.System

type nat is (int n) where n >= 0

function f([nat] xs, [nat] ys, nat i) => nat
requires i < (|xs| + |ys|):
    xs = xs ++ ys
    return xs[i]

method main(System.Console sys) => void:
    left = [1, 2, 3]
    right = [5, 6, 7]
    r = f(left, right, 1)
    sys.out.println(Any.toString(r))
    r = f(left, right, 4)
    sys.out.println(Any.toString(r))
