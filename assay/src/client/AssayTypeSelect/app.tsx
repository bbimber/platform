import React from 'react';
import ReactDOM from 'react-dom';
import { ErrorBoundary } from '@labkey/components';

import { App } from './AssayTypeSelect';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', () => {
    ReactDOM.render(<ErrorBoundary><App/></ErrorBoundary>, document.getElementById('app'));
});