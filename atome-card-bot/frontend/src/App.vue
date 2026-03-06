<template>
  <div id="app">
    <nav class="nav">
      <router-link to="/" class="nav-link">聊天</router-link>
      <span class="nav-separator">|</span>
      <span class="nav-group">
        <router-link to="/admin/kb" class="nav-link">知识库</router-link>
        <router-link to="/admin/intents" class="nav-link">意图</router-link>
        <router-link to="/admin/feedback" class="nav-link">反馈</router-link>
        <router-link to="/admin/overrides" class="nav-link">全局规则</router-link>
      </span>
    </nav>
    <router-view />
  </div>
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
  background: #f5f5f5;
}

.nav {
  background: #fff;
  padding: 12px 20px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-link {
  text-decoration: none;
  color: #333;
  font-size: 14px;
  padding: 6px 12px;
  border-radius: 4px;
  transition: background 0.2s;
}

.nav-link:hover {
  background: #f0f0f0;
}

.nav-link.router-link-active {
  background: #e3f2fd;
  color: #1976d2;
}

.nav-separator {
  color: #ccc;
}

.nav-group {
  display: flex;
  gap: 8px;
}

#app {
  min-height: 100vh;
}
</style>