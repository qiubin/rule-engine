import React, { useState, useEffect } from 'react'
import { Card, Form, Input, Switch, Select, Button, message, InputNumber, Divider } from 'antd'
import { SaveOutlined } from '@ant-design/icons'
import axios from 'axios'

const API = '/api/v1/adapter-config'

export default function AdapterConfigPage() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    fetchConfig()
  }, [])

  const fetchConfig = async () => {
    setLoading(true)
    try {
      const res = await axios.get(`${API}/first`)
      const cfg = res.data || {}
      form.setFieldsValue({
        enabled: cfg.enabled ?? false,
        baseUrl: cfg.baseUrl || '',
        adapterPath: cfg.adapterPath || '/api/v1/adapter/emr',
        authType: cfg.authType || 'none',
        authToken: cfg.authToken || '',
        apiKey: cfg.apiKey || '',
        connectTimeoutMs: cfg.connectTimeoutMs ?? 5000,
        readTimeoutMs: cfg.readTimeoutMs ?? 10000,
      })
    } catch (e) {
      message.error('加载适配器配置失败')
    }
    setLoading(false)
  }

  const handleSave = async (values) => {
    setSaving(true)
    try {
      await axios.post(API, values)
      message.success('适配器配置保存成功')
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
    setSaving(false)
  }

  const authType = Form.useWatch('authType', form)

  return (
    <div style={{ padding: 24, maxWidth: 700 }}>
      <Card title="数据适配器配置" loading={loading}>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item
            name="enabled"
            label="启用适配器"
            valuePropName="checked"
          >
            <Switch checkedChildren="启用" unCheckedChildren="关闭" />
          </Form.Item>

          <Divider />

          <Form.Item
            name="baseUrl"
            label="适配器服务地址"
            rules={[{ required: true, message: '请输入适配器服务地址' }]}
          >
            <Input placeholder="如: http://192.168.5.236:8688" />
          </Form.Item>

          <Form.Item
            name="adapterPath"
            label="适配器路径"
            rules={[{ required: true }]}
          >
            <Input placeholder="如: /api/v1/adapter/emr" />
          </Form.Item>

          <Form.Item
            name="authType"
            label="认证方式"
            rules={[{ required: true }]}
          >
            <Select placeholder="选择认证方式">
              <Select.Option value="none">无认证</Select.Option>
              <Select.Option value="bearer">Bearer Token</Select.Option>
              <Select.Option value="apikey">API Key</Select.Option>
            </Select>
          </Form.Item>

          {authType === 'bearer' && (
            <Form.Item
              name="authToken"
              label="Bearer Token"
              rules={[{ required: true, message: '请输入 Token' }]}
            >
              <Input.Password placeholder="输入 Bearer Token" />
            </Form.Item>
          )}

          {authType === 'apikey' && (
            <Form.Item
              name="apiKey"
              label="API Key"
              rules={[{ required: true, message: '请输入 API Key' }]}
            >
              <Input.Password placeholder="输入 API Key" />
            </Form.Item>
          )}

          <Divider />

          <Form.Item
            name="connectTimeoutMs"
            label="连接超时（毫秒）"
            rules={[{ required: true }]}
          >
            <InputNumber min={1000} max={60000} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="readTimeoutMs"
            label="读取超时（毫秒）"
            rules={[{ required: true }]}
          >
            <InputNumber min={1000} max={60000} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item style={{ marginTop: 24 }}>
            <Button type="primary" icon={<SaveOutlined />} htmlType="submit" loading={saving}>
              保存配置
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
