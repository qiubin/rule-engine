import React, { useState } from 'react'
import ConditionModelMgr from '../ConditionModelMgr'
import DictionaryMgr from '../DictionaryMgr'
import DataElementMgr from '../DataElementMgr'

const TABS = [
  { key: 'models', label: '条件管理' },
  { key: 'dictionaries', label: '字典管理' },
  { key: 'dataElements', label: '数据元管理' },
]

export default function SystemMenu() {
  const [activeTab, setActiveTab] = useState('models')

  const renderContent = () => {
    switch (activeTab) {
      case 'models': return <ConditionModelMgr />
      case 'dictionaries': return <DictionaryMgr />
      case 'dataElements': return <DataElementMgr />
      default: return <ConditionModelMgr />
    }
  }

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', background: '#fff' }}>
      <div style={{ display: 'flex', borderBottom: '1px solid #f0f0f0', background: '#fafafa', padding: '0 16px' }}>
        {TABS.map(tab => (
          <div
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            style={{
              padding: '12px 24px',
              cursor: 'pointer',
              borderBottom: activeTab === tab.key ? '2px solid #1890ff' : '2px solid transparent',
              color: activeTab === tab.key ? '#1890ff' : '#666',
              fontSize: 14,
              fontWeight: activeTab === tab.key ? 'bold' : 'normal',
              transition: 'all 0.3s',
            }}
          >
            {tab.label}
          </div>
        ))}
      </div>
      <div style={{ flex: 1, overflow: 'auto' }}>
        {renderContent()}
      </div>
    </div>
  )
}
