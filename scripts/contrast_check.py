"""WCAG contrast validation for the Harness Aurora palette."""
import itertools


def srgb_to_lin(c: float) -> float:
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hexcolor: str) -> float:
    h = hexcolor.lstrip('#')
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * srgb_to_lin(r) + 0.7152 * srgb_to_lin(g) + 0.0722 * srgb_to_lin(b)


def ratio(fg: str, bg: str) -> float:
    l1, l2 = sorted((luminance(fg), luminance(bg)), reverse=True)
    return (l1 + 0.05) / (l2 + 0.05)


DARK = {
    'bg': '#0B0E14', 'surface': '#12161F', 'surfaceVar': '#1A1F2B',
    'outline': '#2A3040', 'primary': '#7C5CFF', 'secondary': '#22D3EE',
    'success': '#34D399', 'warning': '#FBBF24', 'error': '#F87171',
    'text': '#E8EBF3', 'text2': '#9AA3B8', 'text3': '#818CA3',
    # refinements (round 1 design review)
    'primaryBtn': '#6841E0', 'primaryText': '#A18BFF',
    'primaryCont': '#3B2E80', 'onPrimCont': '#EDE9FF',
    'errorText': '#FCA5A5',
}
LIGHT = {
    'bg': '#F7F8FB', 'surface': '#FFFFFF', 'surfaceVar': '#EEF0F6',
    'outline': '#D5D9E4', 'primary': '#5B3FE0', 'secondary': '#0891B2',
    'success': '#059669', 'warning': '#B45309', 'error': '#DC2626',
    'text': '#0F1320', 'text2': '#5A6378', 'text3': '#646D82',
    # refinements
    'primaryCont': '#E4DCFF', 'onPrimCont': '#2A2050',
    'secondaryText': '#0E7490', 'successText': '#067647', 'errorText': '#B42318',
    'warningText': '#93370D',
}

pairs_dark = [
    ('text/bg', 'text', 'bg'), ('text/surface', 'text', 'surface'), ('text/surfaceVar', 'text', 'surfaceVar'),
    ('text2/bg', 'text2', 'bg'), ('text2/surface', 'text2', 'surface'), ('text2/surfaceVar', 'text2', 'surfaceVar'),
    ('text3/bg', 'text3', 'bg'), ('text3/surface', 'text3', 'surface'), ('text3/surfaceVar', 'text3', 'surfaceVar'),
    ('primaryText/bg (accent as small text)', 'primaryText', 'bg'), ('primaryText/surfaceVar', 'primaryText', 'surfaceVar'),
    ('white/primaryBtn (button fill)', '#FFFFFF', 'primaryBtn'),
    ('onPrimCont/primaryCont (filled tonal)', 'onPrimCont', 'primaryCont'),
    ('secondary/bg', 'secondary', 'bg'), ('success/bg', 'success', 'bg'),
    ('warning/bg', 'warning', 'bg'), ('errorText/bg (error small text)', 'errorText', 'bg'),
    ('errorText/surfaceVar', 'errorText', 'surfaceVar'),
    ('success/surfaceVar', 'success', 'surfaceVar'),
]
pairs_light = [
    ('text/bg', 'text', 'bg'), ('text/surface', 'text', 'surface'), ('text/surfaceVar', 'text', 'surfaceVar'),
    ('text2/bg', 'text2', 'bg'), ('text2/surface', 'text2', 'surface'), ('text2/surfaceVar', 'text2', 'surfaceVar'),
    ('text3/bg', 'text3', 'bg'), ('text3/surface', 'text3', 'surface'), ('text3/surfaceVar', 'text3', 'surfaceVar'),
    ('primary/bg (accent text)', 'primary', 'bg'), ('white/primary (btn)', '#FFFFFF', 'primary'),
    ('onPrimCont/primaryCont (filled tonal)', 'onPrimCont', 'primaryCont'),
    ('secondaryText/surface (small text)', 'secondaryText', 'surface'),
    ('successText/surface (small text)', 'successText', 'surface'), ('successText/surfaceVar', 'successText', 'surfaceVar'),
    ('warningText/surface', 'warningText', 'surface'),
    ('errorText/surface', 'errorText', 'surface'), ('errorText/surfaceVar', 'errorText', 'surfaceVar'),
    ('error/surfaceVar (icons/large)', 'error', 'surfaceVar'),
]


def report(name, theme, pairs):
    print(f'== {name} ==')
    for label, fg, bg in pairs:
        r = ratio(theme.get(fg, fg), theme.get(bg, bg))
        flag = 'PASS AA' if r >= 4.5 else ('AA-large' if r >= 3.0 else 'FAIL')
        print(f'{label:34s} {fg} on {bg}: {r:5.2f}:1  {flag}')


report('DARK', DARK, pairs_dark)
print()
report('LIGHT', LIGHT, pairs_light)
