import React, { useState, useEffect } from 'react'
import { Card, Form, Input, Switch, Button, message, InputNumber, Alert, Divider } from 'antd'
import { SaveOutlined, ThunderboltOutlined } from '@ant-design/icons'
import axios from 'axios'

const API = '/api/v1/db-config'

export default function DbConfigPage() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState(null)

  useEffect(() => {
    fetchConfig()
  }, [])

  const fetchConfig = async () => {
    setLoading(true)
    try {
      const res = await axios.get(API)
      const cfg = res.data || {}
      form.setFieldsValue({
        host: cfg.host || 'localhost',
        port: cfg.port ?? 3306,
        databaseName: cfg.databaseName || 'ruleengine',
        username: cfg.username || 'root',
        password: cfg.password || '',
        useSsl: cfg.useSsl ?? false,
      })
    } catch (e) {
      message.error('加载数据库配置失败')
    }
    setLoading(false)
  }

  const handleSave = async (values) => {
    setSaving(true)
    try {
      await axios.post(API, values)
      message.success('配置已保存，重启后端后生效')
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
    setSaving(false)
  }

  const handleTest = async () => {
    const values = await form.validateFields().catch(() => null)
    if (!values) return
    setTesting(true)
    setTestResult(null)
    try {
      const res = await axios.post(`${API}/test`, values)
      setTestResult(res.data)
    } catch (e) {
      setTestResult({ success: false, message: '测试请求失败: ' + e.message })
    }
    setTesting(false)
  }

  return (
    <div style={{ padding: 24, maxWidth: 700 }}>
      <Card title="数据库连接配置" loading={loading}>
        <Alert
          message="修改数据库配置后，需要重启后端服务才能生效"
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item
            name="host"
            label="数据库主机"
            rules={[{ required: true, message: '请输入数据库主机地址' }]}
          >
            <Input placeholder="如: localhost 或 192.168.1.100" />
          </Form.Item>

          <Form.Item
            name="port"
            label="端口"
            rules={[{ required: true }]}
          >
            <InputNumber min={1} max={65535} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="databaseName"
            label="数据库名称"
            rules={[{ required: true }]}
          >
            <Input placeholder="如: ruleengine" />
          </Form.Item>

          <Divider />

          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true }]}
          >
            <Input placeholder="如: root" />
          </Form.Item>

          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true }]}
          >
            <Input.Password placeholder="请输入数据库密码" />
          </Form.Item>

          <Form.Item
            name="useSsl"
            label="使用 SSL"
            valuePropName="checked"
          >
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>

          {testResult && (
            <Alert
              message={testResult.message}
              type={testResult.success ? 'success' : 'error'}
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}

          <Form.Item style={{ marginTop: 24 }}>
            <Button
              style={{ marginRight: 12 }}
              icon={<ThunderboltOutlined />}
              onClick={handleTest}
              loading={testing}
            >
              测试连接
            </Button>
            <Button type="primary" icon={<SaveOutlined />} htmlType="submit" loading={saving}>
              保存配置
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
