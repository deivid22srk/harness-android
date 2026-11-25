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
    'bg': '#161616', 'surface': '#1C1C1E', 'surfaceVar': '#222223',
    'outline': '#2E2E30', 'primary': '#3D8BFF', 'secondary': '#43B3AE',
    'success': '#4ADE80', 'warning': '#FBBF24', 'error': '#F26A6F',
    'text': '#EDEDED', 'text2': '#A0A0A3', 'text3': '#8A8A8F', 'codeBg': '#0D0D0D',
    'okText': '#63DA93', 'warnTextOnCont': '#FDE68A', 'warnCont': '#453505', 'errText': '#FCA5A5',
    'primaryText': '#6FB2FF',
    'primaryBtn': '#2773DB',
    'primaryCont': '#1D3350', 'onPrimCont': '#D6E6FF',
    'secondaryText': '#5CC7C2', 'userBubble': '#2E2E2E',
}
LIGHT = {
    'bg': '#FAFAFA', 'surface': '#FFFFFF', 'surfaceVar': '#F0F0F2',
    'outline': '#DCDCDF', 'primary': '#1F66C4', 'secondary': '#0E8A84',
    'success': '#0C8A4E', 'warning': '#B45309', 'error': '#C93940',
    'text': '#1B1B1D', 'text2': '#58585E', 'text3': '#6B6B71', 'codeBg': '#F1F1F3',
    'okText': '#0A6B3E', 'warnTextOnCont': '#93370D', 'warnCont': '#FCEDD4', 'errText': '#AD252B',
    'comment': '#5D6579',
    'primaryCont': '#DCEAFF', 'onPrimCont': '#12305C',
    'primaryBtn': '#2773DB',
    'secondaryText': '#0B6B66', 'successText': '#0A6B3E', 'errorText': '#AD252B',
    'warningText': '#93370D',
}

pairs_dark = [
    ('text/bg', 'text', 'bg'), ('text/surface', 'text', 'surface'), ('text/surfaceVar', 'text', 'surfaceVar'),
    ('text/userBubble', 'text', 'userBubble'),
    ('text2/bg', 'text2', 'bg'), ('text2/surface', 'text2', 'surface'), ('text2/surfaceVar', 'text2', 'surfaceVar'),
    ('text3/bg', 'text3', 'bg'), ('text3/surface', 'text3', 'surface'), ('text3/surfaceVar', 'text3', 'surfaceVar'),
    ('primaryText/bg (accent as small text)', 'primaryText', 'bg'), ('primaryText/surfaceVar', 'primaryText', 'surfaceVar'),
    ('white/primaryBtn (button fill)', '#FFFFFF', 'primaryBtn'),
    ('onPrimCont/primaryCont (filled tonal)', 'onPrimCont', 'primaryCont'),
    ('secondaryText/bg (small text)', 'secondaryText', 'bg'), ('success/bg', 'success', 'bg'),
    ('warning/bg', 'warning', 'bg'), ('errorText/bg (error small text)', 'errText', 'bg'),
    ('errorText/surfaceVar', 'errText', 'surfaceVar'),
    ('success/surfaceVar', 'success', 'surfaceVar'),
    ('okText/codeBg (diff/syntax)', 'okText', 'codeBg'),
    ('errText/codeBg (diff - lines)', 'errText', 'codeBg'),
]
pairs_light = [
    ('text/bg', 'text', 'bg'), ('text/surface', 'text', 'surface'), ('text/surfaceVar', 'text', 'surfaceVar'),
    ('text2/bg', 'text2', 'bg'), ('text2/surface', 'text2', 'surface'), ('text2/surfaceVar', 'text2', 'surfaceVar'),
    ('text3/bg', 'text3', 'bg'), ('text3/surface', 'text3', 'surface'), ('text3/surfaceVar', 'text3', 'surfaceVar'),
    ('primaryText/bg (accent text)', 'primary', 'bg'), ('white/primaryBtn (btn)', '#FFFFFF', 'primaryBtn'),
    ('onPrimCont/primaryCont (filled tonal)', 'onPrimCont', 'primaryCont'),
    ('secondaryText/surface (small text)', 'secondaryText', 'surface'),
    ('successText/surface (small text)', 'successText', 'surface'), ('successText/surfaceVar', 'successText', 'surfaceVar'),
    ('warningText/surface', 'warningText', 'surface'),
    ('errorText/surface', 'errorText', 'surface'), ('errorText/surfaceVar', 'errorText', 'surfaceVar'),
    ('error/surfaceVar (icons/large)', 'error', 'surfaceVar'),
    ('okText/surface (diff + lines)', 'okText', 'surface'),
    ('okText/codeBg (diff + lines)', 'okText', 'codeBg'),
    ('errText/codeBg (diff - lines)', 'errText', 'codeBg'),
    ('warnTextOnCont/warnCont (snackbar)', 'warnTextOnCont', 'warnCont'),
    ('comment/codeBg (syntax)', 'comment', 'codeBg'),
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
