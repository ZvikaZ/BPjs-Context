// Add entities
ctx.populateContext(() => {
  ctx.insertEntity('r1', 'room', {subtype: 'kitchen'})
  ctx.insertEntity('r2', 'room', {subtype: 'bedroom'})
  ctx.insertEntity('r3', 'room', {subtype: 'bathroom'})
})

// Specifies the contexts/layers preconditions
ctx.registerQuery('Night',
  entity => entity.id == 'night')
ctx.registerQuery('Room.WithTaps',
  entity => entity.type == 'room' &&
    ['kitchen', 'bathroom'].includes(entity.subtype))

// Specify the effect of certain events on the context
ctx.registerEffect('night begins', function (data) {
  ctx.insertEntity('night', 'system')
})
ctx.registerEffect('night ends', function (data) {
  ctx.removeEntity('night')
})


ctx.registerQuery('Room.Kitchen',
  entity => entity.type == 'room' &&
    'kitchen' == entity.subtype)