//#region HELPER FUNCTIONS
function getCell(i, j) {
  return ctx.getEntityById("cell(" + i + "," + j + ")")
}

function getLineCells(l) {
  return [getCell(l.c1.i, l.c1.j), getCell(l.c2.i, l.c2.j), getCell(l.c3.i, l.c3.j)]
}

//#endregion HELPER FUNCTIONS

//#region EventSets
// Represents Enforce Turns
const move = bp.EventSet("MoveEvents", function (e) {
  return ['X', 'O'].includes(String(e.name))
})
const XEvents = bp.EventSet("XEvents", function (e) {
  return e.name == "X"
})
const OEvents = bp.EventSet("OEvents", function (e) {
  return e.name == "O"
})
const EndGame = bp.EventSet("EndGame", function (e) {
  return ['OWin', 'XWin', 'Draw'].includes(String(e.name))
})

//#endregion EventSets

//#region GAME RULES

//block X,O on nonempty cell
bthread("block X,O on nonempty cell", "Cell.All", function (c) {
  sync({waitFor: [Event("X", c), Event("O", c)]})
  sync({block: [Event("X", c), Event("O", c)]})
})

bthread("EnforceTurnsXO", function () {
  while (true) {
    sync({waitFor: XEvents, block: OEvents})
    sync({waitFor: OEvents, block: XEvents})
  }
})

// Represents when the game ends
bthread("block moves on endgame", function () {
  sync({waitFor: EndGame})
  sync({block: bp.all})
})

// Represents when it is a draw
bthread("DetectDraw", function () {
  for (let i = 0; i < 9; i++) {
    sync({waitFor: move})
  }
  sync({request: Event('Draw')}, 90)
})

// Represents when X wins
bthread("DetectXWin", "Line.All", function (l) {
  let cells = getLineCells(l)
  const events = cells.map(c => Event("X", c))
  for (let c = 0; c < 3; c++) {
    sync({waitFor: events})
  }
  sync({request: Event('XWin')}, 100)
})

// Represents when O wins
bthread("DetectOWin", "Line.All", function (l) {
  let cells = getLineCells(l)
  const events = cells.map(c => Event("O", c))
  for (let c = 0; c < 3; c++) {
    sync({waitFor: events})
  }
  sync({request: Event('OWin')}, 100)
})

//#endregion GAME RULES


//#region PLAYER O STRATEGY

// Player O strategy to add a the third O to win
bthread("AddThirdO", "Line.All", function (l) {
  const cells = getLineCells(l)
  const events = cells.map(c => Event("O", c))
  sync({waitFor: events})
  sync({waitFor: events})
  sync({request: events}, 50)
})

// Player O strategy to prevent the third X of player X
bthread("PreventThirdX", "Line.All", function (l) {
  const cells = getLineCells(l)
  const OEvents = cells.map(c => Event("O", c))
  const XEvents = cells.map(c => Event("X", c))
  sync({waitFor: XEvents})
  sync({waitFor: XEvents})
  sync({request: OEvents})
})

// Player O strategy to prevent fork of player X
bthread("fork", 'Fork.All', function (f) {
  let e1 = Event("X", getCell(f.x[0].i, f.x[0].j))
  let e2 = Event("X", getCell(f.x[1].i, f.x[1].j))
  let O = [];
  for(let i=0; i< f.block.length; i++)
    O.push(Event("O", getCell(f.block[i].i, f.block[i].j)))
  sync({waitFor: [e1, e2]})
  sync({waitFor: [e1, e2]})
  sync({request: O}, 30)
})

// Preference to put O on the center
bthread("Center", "Cell.Center", function (c) {
  sync({request: Event("O", c)}, 35)
})

// Preference to put O on the corners
bthread("Corner", "Cell.Corner", function (c) {
  sync({request: Event("O", c)}, 20)
})

// Preference to put O on the sides
bthread("Sides", "Cell.Sides", function (c) {
  sync({request: Event("O", c)}, 10)
})

//#endregion PLAYER O STRATEGY


bthread("simulate x", "Cell.All", function (cell) {
  sync({request: Event("X", cell)})
})