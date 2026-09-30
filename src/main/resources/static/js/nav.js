// Shared navbar behaviour: shows login state and keeps the cart badge in sync.
// Every page includes a <header class="signboard"> with these element ids already in the markup.

function markActiveNavLink() {
  const here = window.location.pathname === '/' ? '/index.html' : window.location.pathname;
  document.querySelectorAll('.nav-links a[href]').forEach(a => {
    if (a.getAttribute('href') === here) a.classList.add('active');
  });
}

async function initNav() {
  markActiveNavLink();
  const authArea = document.getElementById('nav-auth-area');
  const ordersLink = document.getElementById('nav-orders');

  try {
    const user = await API.me();
    if (authArea) {
      authArea.innerHTML = `
        ${user.role === 'ADMIN' ? '<a href="/admin.html" style="margin-right:18px;">Admin</a>' : ''}
        <span style="margin-right:18px;">Hi, ${escapeHtml(user.name.split(' ')[0])}</span>
        <button class="linklike" id="nav-logout-btn">Logout</button>
      `;
      document.getElementById('nav-logout-btn').addEventListener('click', async () => {
        await API.logout();
        window.location.href = '/index.html';
      });
    }
    if (ordersLink) ordersLink.style.display = '';
  } catch (e) {
    // not logged in - leave default "Login" link in place
    if (ordersLink) ordersLink.style.display = 'none';
  }

  refreshCartCount();
}

async function refreshCartCount() {
  const badge = document.getElementById('cart-count');
  if (!badge) return;
  try {
    const items = await API.cart();
    const total = items.reduce((sum, item) => sum + item.quantity, 0);
    badge.textContent = total;
  } catch (e) {
    badge.textContent = '0';
  }
}

document.addEventListener('DOMContentLoaded', initNav);
