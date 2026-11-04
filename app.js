// search filter for the item pages + close the mobile menu after a click
function filterCards(q) {
  q = q.trim().toLowerCase();
  document.querySelectorAll('.card.item').forEach(function (c) {
    var hit = !q || (c.dataset.search || '').indexOf(q) >= 0 || c.textContent.toLowerCase().indexOf(q) >= 0;
    c.style.display = hit ? '' : 'none';
  });
  document.querySelectorAll('section').forEach(function (s) {
    var cards = s.querySelectorAll('.card.item');
    if (!cards.length) return;
    var any = Array.prototype.some.call(cards, function (c) { return c.style.display !== 'none'; });
    s.style.display = any ? '' : 'none';
  });
}
document.querySelectorAll('nav.side a').forEach(function (a) {
  a.addEventListener('click', function () { document.body.classList.remove('menu'); });
});
