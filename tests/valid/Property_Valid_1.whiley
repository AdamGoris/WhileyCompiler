property nat(int x) where x >= 0

function abs(int x) -> (int y)
ensures nat(y)
ensures (x == y) || (x == -y):
    //
    if x >= 0:
        return x
    else:
        return y

public export method test():
    assert abs(-1) == 1
    assert abs(-2) == 2
    assert abs(0) == 0
    assert abs(1) == 1
    assert abs(2) == 2
    