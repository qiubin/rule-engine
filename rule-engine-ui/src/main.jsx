import React from 'react'
import ReactDOM from 'react-dom'
import App from './App'
import 'antd/dist/reset.css'
import 'reactflow/dist/style.css'

class ErrorBoundary extends React.Component {
  constructor(props) { super(props); this.state = { hasError: false, error: null } }
  static getDerivedStateFromError(error) { return { hasError: true, error } }
  componentDidCatch(error, info) { console.error('React Error:', error, info) }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 40, color: 'red' }}>
          <h2>组件渲染错误</h2>
          <pre>{this.state.error?.toString?.() || '未知错误'}</pre>
        </div>
      )
    }
    return this.props.children
  }
}

ReactDOM.render(
  <ErrorBoundary><App /></ErrorBoundary>,
  document.getElementById('root')
)
