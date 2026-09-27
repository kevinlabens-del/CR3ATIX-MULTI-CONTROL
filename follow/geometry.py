"""Pure FOLLOW transition geometry. Coordinates use layout pixels."""
from dataclasses import dataclass

@dataclass(frozen=True)
class Screen:
    name: str
    x: float
    y: float
    width: float
    height: float
    online: bool = True

    def __post_init__(self):
        if self.width <= 0 or self.height <= 0:
            raise ValueError("screen dimensions must be positive")

def transition(current: Screen, screens: list[Screen], direction: str,
               edge_fraction: float, tolerance: float = 0.0):
    """Return (target, normalized_x, normalized_y) or None.

    A crossing requires overlapping projections at the crossed boundary.
    The closest eligible online screen is selected. Gaps up to tolerance
    permit offset physical layouts without inventing diagonal crossings.
    """
    if direction not in ("left", "right", "up", "down"):
        raise ValueError("invalid direction")
    fraction = max(0.0, min(1.0, edge_fraction))
    horizontal = direction in ("left", "right")
    crossing = current.y + fraction * current.height if horizontal else current.x + fraction * current.width
    candidates = []
    for target in screens:
        if target.name == current.name or not target.online:
            continue
        if horizontal:
            gap = (target.x - (current.x + current.width)) if direction == "right" else (current.x - (target.x + target.width))
            near = target.y
            far = target.y + target.height
        else:
            gap = (target.y - (current.y + current.height)) if direction == "down" else (current.y - (target.y + target.height))
            near = target.x
            far = target.x + target.width
        if gap < -1e-8 or gap > tolerance or not (near <= crossing <= far):
            continue
        position = max(0.0, min(1.0, (crossing - near) / (far - near)))
        candidates.append((gap, target.name, target, position))
    if not candidates:
        return None
    _, _, target, position = min(candidates)
    return (target, 0.0 if direction == "right" else 1.0, position) if horizontal else (
        target, position, 0.0 if direction == "down" else 1.0)
