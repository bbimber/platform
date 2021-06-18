import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { ErrorBoundary } from '@labkey/components';

import { App } from './AssayTypeSelect';

const render = () => {
    ReactDOM.render(
        <AppContainer>
            <ErrorBoundary>
                <App/>
            </ErrorBoundary>
        </AppContainer>,
        document.getElementById('app')
    )
};

declare const module: any;

if (module.hot) {
    module.hot.accept();
}

render();